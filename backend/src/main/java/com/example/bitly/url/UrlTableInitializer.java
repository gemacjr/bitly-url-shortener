package com.example.bitly.url;

import com.example.bitly.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/** Creates the urls table (with TTL on expires_at) in LocalStack / tests. Disabled on real AWS. */
@Component
@ConditionalOnProperty(name = "app.dynamodb.auto-create-table", havingValue = "true")
class UrlTableInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(UrlTableInitializer.class);

    private final DynamoDbClient dynamo;
    private final String table;

    UrlTableInitializer(DynamoDbClient dynamo, AppProperties props) {
        this.dynamo = dynamo;
        this.table = props.dynamodb().tableName();
    }

    @Override
    public void afterPropertiesSet() {
        try {
            dynamo.createTable(b -> b.tableName(table)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .keySchema(KeySchemaElement.builder().attributeName(UrlRepository.SHORT_CODE).keyType(KeyType.HASH).build())
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName(UrlRepository.SHORT_CODE).attributeType(ScalarAttributeType.S).build()));
            dynamo.waiter().waitUntilTableExists(b -> b.tableName(table));
            dynamo.updateTimeToLive(b -> b.tableName(table)
                    .timeToLiveSpecification(t -> t.enabled(true).attributeName(UrlRepository.EXPIRES_AT)));
            log.info("Created DynamoDB table {} with TTL on {}", table, UrlRepository.EXPIRES_AT);
        } catch (ResourceInUseException alreadyExists) {
            log.debug("DynamoDB table {} already exists", table);
        }
    }
}
