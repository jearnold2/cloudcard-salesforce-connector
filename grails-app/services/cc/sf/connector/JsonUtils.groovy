package cc.sf.connector


import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class JsonUtils {
    public static String getValueFromJsonKey(String json, String keyPath) throws IOException {
        if (!json) throw new Exception("No value for JSON object.")
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseTree = objectMapper.readTree(json);
        return responseTree.at(keyPath).asText();
    }

    public static String findValueFromJsonKey(String json, String key) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseTree = objectMapper.readTree(json);
        return responseTree.findValue(key).asText();
    }
}
