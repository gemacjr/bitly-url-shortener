package com.example.bitly.url;

import com.example.bitly.config.AppProperties;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * DynamoDB table keyed by short_code. The partition key gives O(1) lookups for the read path, and
 * {@code attribute_not_exists} plays the role of the relational UNIQUE constraint.
 *
 * <p>{@code expires_at} is epoch seconds and is the table's TTL attribute, so DynamoDB deletes
 * expired rows for free (the "background cleanup job" from the design). TTL deletion is lazy
 * (it can lag by up to a couple of days), so callers still check {@link UrlMapping#isExpired}.
 */
@Repository
public class UrlRepository {

    static final String SHORT_CODE = "short_code";
    static final String LONG_URL = "long_url";
    static final String CREATED_AT = "created_at";
    static final String EXPIRES_AT = "expires_at";
    static final String CUSTOM_ALIAS = "custom_alias";

    private final DynamoDbClient dynamo;
    private final String table;

    public UrlRepository(DynamoDbClient dynamo, AppProperties props) {
        this.dynamo = dynamo;
        this.table = props.dynamodb().tableName();
    }

    /** @return false if the short code is already taken (by a generated code or a custom alias) */
    public boolean saveIfAbsent(UrlMapping mapping) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(SHORT_CODE, AttributeValue.fromS(mapping.shortCode()));
        item.put(LONG_URL, AttributeValue.fromS(mapping.longUrl()));
        item.put(CREATED_AT, AttributeValue.fromN(Long.toString(mapping.createdAt().getEpochSecond())));
        item.put(CUSTOM_ALIAS, AttributeValue.fromBool(mapping.customAlias()));
        if (mapping.expiresAt() != null) {
            item.put(EXPIRES_AT, AttributeValue.fromN(Long.toString(mapping.expiresAt().getEpochSecond())));
        }
        try {
            dynamo.putItem(PutItemRequest.builder()
                    .tableName(table)
                    .item(item)
                    .conditionExpression("attribute_not_exists(" + SHORT_CODE + ")")
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    public Optional<UrlMapping> findByShortCode(String shortCode) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(table)
                .key(Map.of(SHORT_CODE, AttributeValue.fromS(shortCode)))
                .build()).item();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        AttributeValue expires = item.get(EXPIRES_AT);
        return Optional.of(new UrlMapping(
                item.get(SHORT_CODE).s(),
                item.get(LONG_URL).s(),
                Instant.ofEpochSecond(Long.parseLong(item.get(CREATED_AT).n())),
                expires == null ? null : Instant.ofEpochSecond(Long.parseLong(expires.n())),
                Boolean.TRUE.equals(item.get(CUSTOM_ALIAS).bool())));
    }
}
