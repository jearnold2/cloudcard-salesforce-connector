package cc.sf.connector


import cc.sf.connector.example.CloudcardApiException
import cc.sf.connector.object.Event
import cc.sf.connector.object.ClientAuthData
import cc.sf.connector.object.SavedHTTPResponse
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.http.protocol.HTTP
import org.apache.http.util.EntityUtils
import org.eclipse.jetty.util.ajax.JSON
import org.springframework.beans.factory.annotation.Value
import grails.core.GrailsApplication

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Function

@Transactional
@Slf4j
class SubscriptionListenerService {

    def startListening(ClientAuthData authData) {

////        Refresh access token before trying to listen
//        String oldToken = authData.authToken
//        authData.authToken = renewAccessToken(authData)
//        log.warn("Updated SF auth token. OLD: $oldToken, NEW: $authData.authToken")


        BayeuxParameters params = new BayeuxParameters() {

            @Override
            public String bearerToken() {
                return authData.authToken;
            }

            @Override
            public URL host() {
                try {
                    return new URL(authData.baseUrl);
                } catch (MalformedURLException e) {
                    throw new IllegalArgumentException(String.format("Unable to create url: %s", authData.baseUrl), e);
                }
            }
        };

        String orgId
        try {
            orgId = CloudcardApi.getMeOrgId(authData.cloudcardAccessToken);

            log.warn("Successfully logged in to CloudCard: Org ID is $orgId");
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
            throw new RuntimeException(e);
        }

//        NOTE: this consumer is where the connector logic would go.
        Consumer<Map<String, Object>> consumer = { eventDetails ->
            Event eventObject = new Event(eventDetails);
            log.warn("Received SF Event: " + eventObject.getType() + " contact at " + eventObject.getCreatedDate() + " (replayID: " + eventObject.getReplayId() + ")");
            if (eventObject.getType() == "created") CloudcardApi.createPerson(orgId, authData.cloudcardAccessToken, eventObject);
        };

        EmpConnector connector = new EmpConnector(params);

        Function<Boolean, String> bearerTokenProvider = { Boolean reAuth ->
            log.error("bearerTokenProvider called.")
            return renewAccessToken(authData)
        }

        connector.setBearerTokenProvider(bearerTokenProvider);

        connector.start().get(5, TimeUnit.SECONDS);

        TopicSubscription subscription = connector.subscribe(authData.topic, authData.replayFrom, consumer).get(5, TimeUnit.SECONDS);

        log.warn(String.format("Subscribed: %s", subscription));
    }


    public static String renewAccessToken(ClientAuthData client) throws IOException {
        CloseableHttpClient connection = HttpClients.createDefault();
        HttpPost request = new HttpPost(client.baseUrl + "/services/oauth2/token");

        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("grant_type", "refresh_token"))
        nvps.add(new BasicNameValuePair("client_id", client.clientId))
        nvps.add(new BasicNameValuePair("client_secret", client.clientSecret))
        nvps.add(new BasicNameValuePair("refresh_token", client.refreshToken))

        request.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
        request.setHeader("Accept", "application/json");
//        This header might not be necessary since using UrlEncodedFormEntity
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        CloseableHttpResponse response = connection.execute(request);

        SavedHTTPResponse savedResponse = new SavedHTTPResponse(EntityUtils.toString(response.getEntity()), response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
        String newToken = JsonUtils.getValueFromJsonKey(savedResponse.getContent(), "/access_token")
        connection.close();
        return newToken;
    }
}
