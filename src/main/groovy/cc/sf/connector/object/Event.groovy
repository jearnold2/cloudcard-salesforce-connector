package cc.sf.connector.object

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.jetty.util.ajax.JSON;

import java.time.ZonedDateTime;

public class Event {
    ZonedDateTime createdDate;
    int replayId;
    String type;

    String email;
    String identifier;
    String name;

    public Event(ZonedDateTime createdDate, int replayId, String type) {
        this.createdDate = createdDate;
        this.replayId = replayId;
        this.type = type;
    }

    public Event(Map<String, Object> eventDetails) {
        System.out.println(JSON.toString(eventDetails));
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode json = null;
        ZonedDateTime datetime = null;
        try {
            json = objectMapper.readTree(JSON.toString(eventDetails));
            datetime = ZonedDateTime.parse(json.at("/event/createdDate").asText());
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.createdDate = datetime
        this.replayId = json.at("/event/replayId").asInt()
        this.type = json.at("/event/type").asText()
        this.name = json.at("/sobject/Name").asText()
        this.email = json.at("/sobject/Email").asText()
        this.identifier = json.at("/sobject/Id").asText()
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public int getReplayId() {
        return replayId;
    }

    public void setReplayId(int replayId) {
        this.replayId = replayId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
