package cc.sf.connector.object

class ClientAuthData {
    String name
    String baseUrl
    String clientId
    String clientSecret
    String authToken
    String refreshToken
    String topic
    Long replayFrom = -1L

    String cloudcardAccessToken

    ClientAuthData(String name, String baseUrl, String clientId, String clientSecret, String authToken, String refreshToken, String topic, String ccAccessToken) {
        this.name = name
        this.baseUrl = baseUrl
        this.clientId = clientId
        this.clientSecret = clientSecret
        this.authToken = authToken
        this.refreshToken = refreshToken
        this.topic = topic
        this.cloudcardAccessToken = ccAccessToken
    }
}
