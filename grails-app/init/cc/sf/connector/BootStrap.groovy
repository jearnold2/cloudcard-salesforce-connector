package cc.sf.connector

import cc.sf.connector.object.ClientAuthData
import com.amazonaws.services.dynamodbv2.document.Item
import org.springframework.beans.factory.annotation.Value

class BootStrap {

    @Value('${test.property}')
    String testProp

    def init = { servletContext ->
        DynamoDBService dbService = new DynamoDBService()
        List<Object> tenants = dbService.getTenants(dbService.connect())

        List<ClientAuthData> activeClients = new ArrayList<>()

        tenants.each {
            String tenant = dbService.getFieldValue(it as Item, "Tenant")
            String ccAuthToken = dbService.getFieldValue(it as Item, "ccAuthToken")
            String baseUrl = dbService.getFieldValue(it as Item, "BaseUrl")
            String topic = dbService.getFieldValue(it as Item, "Topic")
            String clientId = dbService.getFieldValue(it as Item, "ClientId")
            String clientSecret = dbService.getFieldValue(it as Item, "ClientSecret")
            String authToken = dbService.getFieldValue(it as Item, "AccessToken")
            String refreshToken = dbService.getFieldValue(it as Item, "RefreshToken")

            log.warn("Loaded Tenant: $tenant")
            log.warn("---------------")
            log.warn("    CloudCard Token : $ccAuthToken")
            log.warn("Salesforce Base URL : $baseUrl")
            log.warn("          Topic URL : $topic")
            log.warn("---------------")

            ClientAuthData client = new ClientAuthData(tenant, baseUrl, clientId, clientSecret, authToken, refreshToken, topic, ccAuthToken)
            activeClients.add(client)

        }
        SubscriptionListenerService listenerService = new SubscriptionListenerService()

        activeClients.each {
            listenerService.startListening(it);
        }

    }
    def destroy = {
    }
}
