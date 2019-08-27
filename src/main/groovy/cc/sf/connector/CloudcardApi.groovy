package cc.sf.connector

import cc.sf.connector.example.CloudcardApiException
import cc.sf.connector.object.Event;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cc.sf.connector.object.SavedHTTPResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cc.sf.connector.JsonUtils

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

public class CloudcardApi {

    final static Logger log = LoggerFactory.getLogger(CloudcardApi.class);
    static final String BASE_URL = "https://test-api.onlinephotosubmission.com/api";

    public static String login(String username, String password) throws IOException {
        String json = "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}";
            SavedHTTPResponse loginResponse = post("/login", json, null);
            if (loginResponse.getResponseCode() != 200)
                throw new IOException("Received " + loginResponse.getResponseCode() + " when attempting to login. Is username & password correct?");
            return JsonUtils.findValueFromJsonKey(loginResponse.getContent(), "access_token");
    }

    public static String getMeOrgId(String accessToken) throws IOException {
        SavedHTTPResponse meResponse = get("/me", accessToken);
        String orgId = ""
        try {
            orgId = JsonUtils.getValueFromJsonKey(meResponse.getContent(), "/organization/id")
        } catch(Exception e) {
            throw new Exception("Could not get org ID for user.", e)
        }
        return orgId
    }

    private static void loginToCloudcard(Map<String, String> arguments){
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost loginPost = new HttpPost("https://api.onlinephotosubmission.com/api/login");

        String loginJsonBody = "{\"username\": \"" + arguments.get("cloudcard_username") + "\", \"password\": \"" + arguments.get("cloudcard_password") + "\"}";
        StringEntity entity = null;
        try {
            entity = new StringEntity(loginJsonBody);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        loginPost.setEntity(entity);
        loginPost.setHeader("Accept", "application/json");
        loginPost.setHeader("Content-Type", "application/json");

        try {
            CloseableHttpResponse response = httpClient.execute(loginPost);
            String responseBody = EntityUtils.toString(response.getEntity());

            String accessToken = JsonUtils.findValueFromJsonKey(responseBody, "access_token");

            if (responseBody.contains("EmptyInputStream")) responseBody = "";
            int responseCode = response.getStatusLine().getStatusCode();
            String responsePhrase = response.getStatusLine().getReasonPhrase();

            log.info("login request returned " + responseCode + " - " + responsePhrase + ", accessToken = " + accessToken);
            httpClient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void createPerson(String orgId, String accessToken, Event eventObject) {
        String personJson = "{\"email\": \"" + eventObject.getEmail() + "\", \"organization\": {\"id\": " + orgId + "}, \"identifier\": \"" + eventObject.getIdentifier() + "\"}";
        try {
            SavedHTTPResponse personSaveResponse = post("/people?sendInvitation=false", personJson, accessToken)
            if (personSaveResponse.getResponseCode() != 201) throw new CloudcardApiException("Received " + personSaveResponse.getResponseCode() + " when attempting to save person.");
            else log.warn("Successfully created new person.");
        } catch (IOException e) {
            log.warn("An exception occurred when POSTing to the person endpoint.", e);
        } catch (CloudcardApiException e) {
            log.warn("Failed to save person:", e);
        }
    }

    static SavedHTTPResponse post(String endpoint, String json, String authToken) throws IOException {
        CloseableHttpClient connection = HttpClients.createDefault();
        HttpPost request = new HttpPost(BASE_URL + endpoint);
        StringEntity body = null;
        body = new StringEntity(json);
        request.setEntity(body);
        request.setHeader("Accept", "application/json");
        request.setHeader("Content-Type", "application/json");
        if (authToken != null) request.setHeader("X-Auth-Token", authToken);
        CloseableHttpResponse response = connection.execute(request);
        SavedHTTPResponse savedResponse = new SavedHTTPResponse(EntityUtils.toString(response.getEntity()), response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
        connection.close();
        return savedResponse;
    }

    static SavedHTTPResponse get(String endpoint, String authToken) throws IOException {
        CloseableHttpClient connection = HttpClients.createDefault();
        HttpGet request = new HttpGet(BASE_URL + endpoint);
        request.setHeader("Accept", "application/json");
        request.setHeader("Content-Type", "application/json");
        if (authToken != null) request.setHeader("X-Auth-Token", authToken);
        CloseableHttpResponse response = connection.execute(request);
        SavedHTTPResponse savedResponse = new SavedHTTPResponse(EntityUtils.toString(response.getEntity()), response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
        connection.close();
        return savedResponse;
    }
}
