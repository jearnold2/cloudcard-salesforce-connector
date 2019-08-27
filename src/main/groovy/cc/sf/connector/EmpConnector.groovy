package cc.sf.connector

import cc.sf.connector.object.SavedHTTPResponse
import org.apache.http.HttpResponse
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.http.protocol.HTTP
import org.apache.http.util.EntityUtils
import org.cometd.bayeux.Channel

/*
 * Copyright (c) 2016, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.TXT file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

import org.cometd.bayeux.Message
import org.cometd.bayeux.client.ClientSessionChannel
import org.cometd.client.BayeuxClient
import org.cometd.client.transport.LongPollingTransport
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.Request
import org.omg.CORBA.NameValuePair
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Function

/**
 * @author hal.hildebrand* @since API v37.0
 */
class EmpConnector {
    private static final String ERROR = "error"
    private static final String FAILURE = "failure"

    private class SubscriptionImpl implements TopicSubscription {
        private final String topic
        private final Consumer<Map<String, Object>> consumer

        private SubscriptionImpl(String topic, Consumer<Map<String, Object>> consumer) {
            this.topic = topic
            this.consumer = consumer
            subscriptions.add(this)
        }

        /*
         * (non-Javadoc)
         * @see com.salesforce.emp.connector.Subscription#cancel()
         */

        @Override
        void cancel() {
            replay.remove(topicWithoutQueryString(topic))
            if (running.get() && client != null) {
                client.getChannel(topic).unsubscribe()
                subscriptions.remove(this)
            }
        }

        /*
         * (non-Javadoc)
         * @see com.salesforce.emp.connector.Subscription#getReplay()
         */

        @Override
        long getReplayFrom() {
            return replay.getOrDefault(topicWithoutQueryString(topic), REPLAY_FROM_EARLIEST)
        }

        /*
         * (non-Javadoc)
         * @see com.salesforce.emp.connector.Subscription#getTopic()
         */

        @Override
        String getTopic() {
            return topic
        }

        @Override
        String toString() {
            return String.format("Subscription [%s:%s]", getTopic(), getReplayFrom())
        }

        Future<TopicSubscription> subscribe() {
            Long replayFrom = getReplayFrom()
            ClientSessionChannel channel = client.getChannel(topic)
            CompletableFuture<TopicSubscription> future = new CompletableFuture<>()
            channel.subscribe({ c, message -> consumer.accept(message.getDataAsMap()) }, { c, message ->
                if (message.isSuccessful()) {
                    future.complete(this)
                } else {
                    Object error = message.get(ERROR)
                    if (error == null) {
                        error = message.get(FAILURE)
                    }
                    future.completeExceptionally(
                            new CannotSubscribe(parameters.endpoint(), topic, replayFrom, error != null ? error : message))
                }
            })
            return future
        }
    }

    public static long REPLAY_FROM_EARLIEST = -2L
    public static long REPLAY_FROM_TIP = -1L

    private static String AUTHORIZATION = "Authorization"
    private static final Logger log = LoggerFactory.getLogger(EmpConnector.class)

    private volatile BayeuxClient client
    private final HttpClient httpClient
    private final BayeuxParameters parameters
    private final ConcurrentMap<String, Long> replay = new ConcurrentHashMap<>()
    private final AtomicBoolean running = new AtomicBoolean()

    private final Set<SubscriptionImpl> subscriptions = new CopyOnWriteArraySet<>()
    private final Set<MessageListenerInfo> listenerInfos = new CopyOnWriteArraySet<>()

    private Function<Boolean, String> bearerTokenProvider
    private AtomicBoolean reauthenticate = new AtomicBoolean(false)

    EmpConnector(BayeuxParameters parameters) {
        this.parameters = parameters
        httpClient = new HttpClient(parameters.sslContextFactory())
        httpClient.getProxyConfiguration().getProxies().addAll(parameters.proxies())
    }

    /**
     * Start the connector.
     * @return true if connection was established, false otherwise
     */
    Future<Boolean> start() {
        if (running.compareAndSet(false, true)) {
            addListener(Channel.META_CONNECT, new AuthFailureListener())
            addListener(Channel.META_HANDSHAKE, new AuthFailureListener())
            replay.clear()
            try {
                Future<Boolean> connect = connect()
                return connect
            } catch (ExecutionException e) {
                return connect()
            }
//            return connect()
        }
        CompletableFuture<Boolean> future = new CompletableFuture<Boolean>()
        future.complete(true)
        return future
    }


    public static String renewAccessToken(String baseUrl, String clientId, String clientSecret, String refreshToken) throws IOException {
        CloseableHttpClient connection = HttpClients.createDefault();
        HttpPost request = new HttpPost(baseUrl + "/services/oauth2/token");

        List<org.apache.http.NameValuePair> nvps = new ArrayList<org.apache.http.NameValuePair>();
        nvps.add(new BasicNameValuePair("grant_type", "refresh_token"))
        nvps.add(new BasicNameValuePair("client_id", clientId))
        nvps.add(new BasicNameValuePair("client_secret", clientSecret))
        nvps.add(new BasicNameValuePair("refresh_token", refreshToken))

        request.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
        request.setHeader("Accept", "application/json");
//        This header might not be necessary since using UrlEncodedFormEntity
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        CloseableHttpResponse response = connection.execute(request);

        SavedHTTPResponse savedResponse = new SavedHTTPResponse(EntityUtils.toString(response.getEntity()), response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
        String newToken = CloudcardApi.getValueFromJsonKey(savedResponse.getContent(), "/access_token")
        connection.close();
        return newToken;
    }

    /**
     * Stop the connector
     */
    void stop() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        if (client != null) {
            log.info("Disconnecting Bayeux Client in EmpConnector")
            client.disconnect()
            client = null
        }
        if (httpClient != null) {
            try {
                httpClient.stop()
            } catch (Exception e) {
                log.error("Unable to stop HTTP transport[{}]", parameters.endpoint(), e)
            }
        }
    }

    /**
     * Disconnect the connector
     */
    void disconnect() {
        if (!running.compareAndSet(true, false)) {
            return
        }
        if (client != null) {
            log.info("Disconnecting Bayeux Client in EmpConnector")
            client.disconnect()
            client = null
        }
    }

    /**
     * Set a bearer token / session id provider function that takes a boolean as input and returns a valid token.
     * If the input is true, the provider function is supposed to re-authenticate with the Salesforce server
     * and get a fresh session id or token.
     *
     * @param bearerTokenProvider a bearer token provider function.
     */
    void setBearerTokenProvider(Function<Boolean, String> bearerTokenProvider) {
        this.bearerTokenProvider = bearerTokenProvider
    }

    /**
     * Subscribe to a topic, receiving events after the replayFrom position
     *
     * @param topic
     *            - the topic to subscribe to
     * @param replayFrom
     *            - the replayFrom position in the event stream
     * @param consumer
     *            - the consumer of the events
     * @return a Future returning the Subscription - on completion returns a Subscription or throws a CannotSubscribe
     *         exception
     */
    Future<TopicSubscription> subscribe(String topic, long replayFrom, Consumer<Map<String, Object>> consumer) {
        if (!running.get()) {
            throw new IllegalStateException(String.format("Connector[%s} has not been started",
                    parameters.endpoint()))
        }
        topic = topic.replaceAll('/$', '')

        final String topicWithoutQueryString = topicWithoutQueryString(topic)
        if (replay.putIfAbsent(topicWithoutQueryString, replayFrom) != null) {
            throw new IllegalStateException(String.format("Already subscribed to %s [%s]",
                    topic, parameters.endpoint()))
        }

        SubscriptionImpl subscription = new SubscriptionImpl(topic, consumer)

        return subscription.subscribe()
    }

    /**
     * Subscribe to a topic, receiving events from the earliest event position in the stream
     *
     * @param topic
     *            - the topic to subscribe to
     * @param consumer
     *            - the consumer of the events
     * @return a Future returning the Subscription - on completion returns a Subscription or throws a CannotSubscribe
     *         exception
     */
    Future<TopicSubscription> subscribeEarliest(String topic, Consumer<Map<String, Object>> consumer) {
        return subscribe(topic, REPLAY_FROM_EARLIEST, consumer)
    }

    /**
     * Subscribe to a topic, receiving events from the latest event position in the stream
     *
     * @param topic
     *            - the topic to subscribe to
     * @param consumer
     *            - the consumer of the events
     * @return a Future returning the Subscription - on completion returns a Subscription or throws a CannotSubscribe
     *         exception
     */
    Future<TopicSubscription> subscribeTip(String topic, Consumer<Map<String, Object>> consumer) {
        return subscribe(topic, REPLAY_FROM_TIP, consumer)
    }

    EmpConnector addListener(String channel, ClientSessionChannel.MessageListener messageListener) {
        listenerInfos.add(new MessageListenerInfo(channel, messageListener))
        return this
    }

    boolean isConnected() {
        return client != null && client.isConnected()
    }

    boolean isDisconnected() {
        return client == null || client.isDisconnected()
    }

    boolean isHandshook() {
        return client != null && client.isHandshook()
    }

    long getLastReplayId(String topic) {
        return replay.get(topic)
    }

    private static String topicWithoutQueryString(String fullTopic) {
        return fullTopic.split("\\?")[0]
    }

    private Future<Boolean> connect() {
        log.info("EmpConnector connecting")
        CompletableFuture<Boolean> future = new CompletableFuture<>()

            try {
                httpClient.start()
            } catch (Exception e) {
                log.error("Unable to start HTTP transport[{}]", parameters.endpoint(), e)
                    running.set(false)
                    future.complete(false)
                    return future
            }

        String bearerToken = bearerToken()

        LongPollingTransport httpTransport = new LongPollingTransport(parameters.longPollingOptions(), httpClient) {
            @Override
            protected void customize(Request request) {
                request.header(AUTHORIZATION, bearerToken)
            }
        }

        client = new BayeuxClient(parameters.endpoint().toExternalForm(), httpTransport)

        client.addExtension(new ReplayExtension(replay))

        addListeners(client)

        client.handshake({ c, m ->
            if (!m.isSuccessful()) {
                Object error = m.get(ERROR)
                Object advice = m.get("ext")
                if (error == null) {
                    error = m.get(FAILURE)
                }
                future.completeExceptionally(new ConnectException(
                        String.format("Cannot connect [%s] : %s : %s", parameters.endpoint(), error, advice)))
                running.set(false)
            } else {
                subscriptions.forEach(SubscriptionImpl.&subscribe)
                future.complete(true)
            }
        })

        return future
    }

    private void addListeners(BayeuxClient client) {
        for (MessageListenerInfo info : listenerInfos) {
            client.getChannel(info.getChannelName()).addListener(info.getMessageListener())
        }
    }

    private String bearerToken() {
        String bearerToken
        if (bearerTokenProvider != null) {
            bearerToken = bearerTokenProvider.apply(reauthenticate.get())
            reauthenticate.compareAndSet(true, false)
        } else {
            bearerToken = parameters.bearerToken()
        }

        return bearerToken
    }

    private void reconnect() {
        if (running.compareAndSet(false, true)) {
            connect()
        } else {
            log.error("The current value of running is not as we expect, this means our reconnection may not happen")
        }
    }

    /**
     * Listens to /meta/connect channel messages and handles 401 errors, where client needs
     * to reauthenticate.
     */
    private class AuthFailureListener implements ClientSessionChannel.MessageListener {
        private static final String ERROR_401 = "401"
        private static final String ERROR_403 = "403"

        @Override
        void onMessage(ClientSessionChannel channel, Message message) {
            if (!message.isSuccessful()) {
                if (isError(message, ERROR_401) || isError(message, ERROR_403)) {
                    reauthenticate.set(true)
                    stop()
                    reconnect()
                }
            }
        }

        private boolean isError(Message message, String errorCode) {
            String error = (String) message.get(Message.ERROR_FIELD)
            String failureReason = getFailureReason(message)

            return (error != null && error.startsWith(errorCode)) ||
                    (failureReason != null && failureReason.startsWith(errorCode))
        }

        private String getFailureReason(Message message) {
            String failureReason = null
            Map<String, Object> ext = message.getExt()
            if (ext != null) {
                Map<String, Object> sfdc = (Map<String, Object>) ext.get("sfdc")
                if (sfdc != null) {
                    failureReason = (String) sfdc.get("failureReason")
                }
            }
            return failureReason
        }
    }

    private static class MessageListenerInfo {
        private String channelName
        private ClientSessionChannel.MessageListener messageListener

        MessageListenerInfo(String channelName, ClientSessionChannel.MessageListener messageListener) {
            this.channelName = channelName
            this.messageListener = messageListener
        }

        String getChannelName() {
            return channelName
        }

        ClientSessionChannel.MessageListener getMessageListener() {
            return messageListener
        }
    }
}
