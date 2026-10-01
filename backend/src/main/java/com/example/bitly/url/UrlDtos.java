package com.example.bitly.url;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Wire format follows the hellointerview API: snake_case fields. */
public final class UrlDtos {

    private UrlDtos() {}

    public record ShortenRequest(
            @JsonProperty("long_url") @NotBlank @Size(max = UrlRules.MAX_URL_LENGTH) String longUrl,
            @JsonProperty("custom_alias") String customAlias,
            @JsonProperty("expiration_date") Instant expirationDate) {}

    public record UrlResponse(
            @JsonProperty("short_url") String shortUrl,
            @JsonProperty("short_code") String shortCode,
            @JsonProperty("long_url") String longUrl,
            @JsonProperty("custom_alias") boolean customAlias,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("expiration_date") Instant expirationDate,
            @JsonProperty("expired") boolean expired) {}
}
