/*
 * Copyright (c) 2016, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.TXT file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package cc.sf.connector.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cc.sf.connector.BayeuxParameters;
import cc.sf.connector.CloudcardApi;
import cc.sf.connector.EmpConnector;
import cc.sf.connector.TopicSubscription;
import cc.sf.connector.object.Event;
import cc.sf.connector.object.SavedHTTPResponse;
import org.eclipse.jetty.util.ajax.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static cc.sf.connector.LoginHelper.login;

/**
 * An example of using the EMP connector using login credentials
 *
 * @author hal.hildebrand
 * @since API v37.0
 */
public class LoginExample {
    final static Logger log = LoggerFactory.getLogger(LoginExample.class);

    public static void main(String[] argv) throws Exception {
        if (argv.length < 5 || argv.length > 6) {
            System.err.println("Usage: LoginExample <Salesforce_username> <Salesforce_password> <Cloudcard_username> <Cloudcard_password> <topic_url> [replayFrom]");
            System.exit(1);
        }

        Map<String, String> arguments = new HashMap<>();
        arguments.put("salesforce_username", argv[0]);
        arguments.put("salesforce_password", argv[1]);
        arguments.put("cloudcard_username", argv[2]);
        arguments.put("cloudcard_password", argv[3]);
        arguments.put("topic", argv[4]);
        if (argv.length == 6) {
            arguments.put("replayFrom", argv[5]);
        }

        long replayFrom = EmpConnector.REPLAY_FROM_TIP;
        if (arguments.containsKey("replayFrom")) {
            replayFrom = Long.parseLong(arguments.get("replayFrom"));
        }

        BearerTokenProvider tokenProvider = new BearerTokenProvider({ ->
            try {
                arguments.put("cloudcard_access_token", CloudcardApi.login(arguments.get("cloudcard_username"), arguments.get("cloudcard_password")));
                arguments.put("cloudcard_org_id", CloudcardApi.getMeOrgId(arguments.get("cloudcard_access_token")));

                log.info("Successfully logged in to CloudCard: Access token is " + arguments.get("cloudcard_access_token"));

                return login(arguments.get("salesforce_username"), arguments.get("salesforce_password"));
            } catch (Exception e) {
                e.printStackTrace(System.err);
                System.exit(1);
                throw new RuntimeException(e);
            }
        });

        BayeuxParameters params = tokenProvider.login();

//        NOTE: this consumer is where the connector logic would go.
        Consumer<Map<String, Object>> consumer = { event ->
            Event eventObject = convertJsonToEventObject(event);
            log.info("Received SF Event: " + eventObject.getType() + " contact at " + eventObject.getCreatedDate() + " (replayID: " + eventObject.getReplayId() + ")");
            if (eventObject.getType().equals("created")) createCloudcardPerson(arguments, eventObject);
        };

        EmpConnector connector = new EmpConnector(params);

        connector.setBearerTokenProvider(tokenProvider);


        connector.start().get(5, TimeUnit.SECONDS);

        TopicSubscription subscription = connector.subscribe(arguments.get("topic"), replayFrom, consumer).get(5, TimeUnit.SECONDS);

        log.info(String.format("Subscribed: %s", subscription));
    }

    private static void createCloudcardPerson(Map<String, String> arguments, Event eventObject) {
        String personJson = "{\"email\": \"" + eventObject.getEmail() + "\", \"organization\": {\"id\": " + arguments.get("cloudcard_org_id") + "}, \"identifier\": \"" + eventObject.getIdentifier() + "\"}";
        try {
            SavedHTTPResponse personSaveResponse = CloudcardApi.post("/people?sendInvitation=false", personJson, arguments.get("cloudcard_access_token"));
            if (personSaveResponse.getResponseCode() != 201) throw new CloudcardApiException("Received " + personSaveResponse.getResponseCode() + " when attempting to save person.");
            else log.info("Successfully created new person.");
        } catch (IOException e) {
            log.info("An exception occurred when POSTing to the person endpoint.", e);
        } catch (CloudcardApiException e) {
            log.info("Failed to save person:", e);
        }
    }

    private static Event convertJsonToEventObject(Map<String, Object> event) {
        log.info(JSON.toString(event));
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode json = null;
        ZonedDateTime datetime = null;
        try {
            json = objectMapper.readTree(JSON.toString(event));
            datetime = ZonedDateTime.parse(json.at("/event/createdDate").asText());
        } catch (IOException e) {
            e.printStackTrace();
        }

        Event eventObject = new Event(datetime, json.at("/event/replayId").asInt(), json.at("/event/type").asText());
        eventObject.setName(json.at("/sobject/Name").asText());
        eventObject.setEmail(json.at("/sobject/Email").asText());
        eventObject.setIdentifier(json.at("/sobject/Id").asText());
        return eventObject;
    }
}
