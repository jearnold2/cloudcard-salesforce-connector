package cc.sf.connector.object;

public class SavedHTTPResponse {
    String content;
    int responseCode;
    String responseName;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseName() {
        return responseName;
    }

    public void setResponseName(String responseName) {
        this.responseName = responseName;
    }

    public SavedHTTPResponse(String content, int responseCode, String responseName) {
        this.content = content;
        this.responseCode = responseCode;
        this.responseName = responseName;
    }
}
