package cc.sf.connector

import org.eclipse.jetty.client.ProxyConfiguration;/*
 * Copyright (c) 2016, salesforce.com, inc. All rights reserved. Licensed under the BSD 3-Clause license. For full
 * license text, see LICENSE.TXT file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author hal.hildebrand
 * @since API v37.0
 */
public class BayeuxParameters {

    /**
     * @return the bearer token used to authenticate
     */
    String bearerToken() {
        return "";
    }

    /**
     * @return the URL of the platform Streaming API endpoint
     */
    URL endpoint() {
        String path = new StringBuilder().append(LoginHelper.COMETD_REPLAY).append(version()).toString();
        try {
            return new URL(host(), path);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(
                    String.format("Unable to create url: %s:%s", host().toExternalForm(), path), e);
        }
    }

    URL host() {
        try {
            return new URL(LoginHelper.LOGIN_ENDPOINT);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(String.format("Unable to form URL for %s", LoginHelper.LOGIN_ENDPOINT));
        }
    }

    /**
     * @return the keep alive interval duration
     */
    long keepAlive() {
        return 60;
    }

    /**
     * @return keep alive interval time unit
     */
    TimeUnit keepAliveUnit() {
        return TimeUnit.MINUTES;
    }

    Map<String, Object> longPollingOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("maxNetworkDelay", maxNetworkDelay());
        options.put("maxBufferSize", maxBufferSize());
        return options;
    }

    /**
     * @return The long polling transport maximum number of bytes of a HTTP response, which may contain many Bayeux
     *         messages
     */
    int maxBufferSize() {
        return 1048576;
    }

    /**
     * @return The long polling transport maximum number of milliseconds to wait before considering a request to the
     *         Bayeux server failed
     */
    int maxNetworkDelay() {
        return 15000;
    }

    /**
     * @return a list of proxies to use for outbound connections
     */
    Collection<? extends ProxyConfiguration.Proxy> proxies() {
        return Collections.emptyList();
    }

    /**
     * @return the SslContextFactory for establishing secure outbound connections
     */
    SslContextFactory sslContextFactory() {
        return new SslContextFactory();
    }

    /**
     * @return the Streaming API version
     */
    String version() {
        return "43.0";
    }
}
