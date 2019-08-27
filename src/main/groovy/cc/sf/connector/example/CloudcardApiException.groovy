package cc.sf.connector.example;

public class CloudcardApiException extends Exception {
    public CloudcardApiException(String s) {
        super(s);
    }
    public CloudcardApiException(String s, Throwable err) {
        super(s, err);
    }
}
