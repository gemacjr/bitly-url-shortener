package com.example.bitly.url;

import java.time.Instant;

/**
 * One row of the urls table.
 *
 * @param expiresAt null means the link never expires
 * @param customAlias true when the user chose the code, false when it came from the counter
 */
public record UrlMapping(String shortCode, String longUrl, Instant createdAt, Instant expiresAt, boolean customAlias) {

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
