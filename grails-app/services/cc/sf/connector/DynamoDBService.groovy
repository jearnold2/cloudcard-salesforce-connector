package cc.sf.connector

import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.document.ItemCollection
import com.amazonaws.services.dynamodbv2.document.ScanOutcome
import com.amazonaws.services.dynamodbv2.document.Table
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec
import grails.gorm.transactions.Transactional

@Transactional
class DynamoDBService {

    public Table connect() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("dynamodb.ca-central-1.amazonaws.com", "ca-central-1"))
                .build();
        DynamoDB dynamoDB = new DynamoDB(client);
        Table table = dynamoDB.getTable("cc-sf-connector");
    }

    public String getSetting(String setting) {
        return getSetting(connect(), setting)
    }

    public String getSetting(Table table, String setting) {
        try {
            GetItemSpec spec = new GetItemSpec().withPrimaryKey("Tenant", "test-account", "Setting", "test record");
            Item outcome = table.getItem(spec);
            return outcome.get("Value").toString();
        }
        catch (Exception e) {
            throw new Exception("Failed to get setting $setting.")
        }
    }

    public String getFieldValue(Item item, String key) {
        return item.get(key).toString()
    }

    public List<Object> getTenants(Table table) {
        try {
            ScanSpec scanSpec = new ScanSpec()
            ItemCollection<ScanOutcome> items = table.scan(scanSpec)
            return items.asList()
        } catch (Exception e) {
            log.warn("Unable to scan the table")
            log.warn(e.getMessage())
        }
    }
}
