package com.example.bitly.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(String baseUrl, DynamoDb dynamodb, Counter counter, Cache cache) {

    public record DynamoDb(String tableName, boolean autoCreateTable) {}

    /** Each instance reserves {@code batchSize} counter values per Redis round trip. */
    public record Counter(String key, int batchSize) {}

    /** Upper bound for a cached redirect; entries never outlive the URL's own expiration. */
    public record Cache(Duration maxTtl) {}

    public String shortUrl(String shortCode) {
        return baseUrl.replaceAll("/+$", "") + "/" + shortCode;
    }
}
