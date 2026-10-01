package com.example.bitly.url;

import com.example.bitly.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cache-aside for redirects (the read path is ~1000x the write path).
 *
 * <p>Availability over consistency: if Redis is down we log and fall through to DynamoDB rather
 * than failing the redirect.
 */
@Component
public class UrlCache {

    private static final Logger log = LoggerFactory.getLogger(UrlCache.class);
    private static final String PREFIX = "bitly:url:";

    private final StringRedisTemplate redis;
    private final Duration maxTtl;

    public UrlCache(StringRedisTemplate redis, AppProperties props) {
        this.redis = redis;
        this.maxTtl = props.cache().maxTtl();
    }

    public Optional<String> get(String shortCode) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIX + shortCode));
        } catch (RuntimeException e) {
            log.warn("Redirect cache read failed for {}, falling back to DynamoDB: {}", shortCode, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(UrlMapping mapping, Instant now) {
        Optional<Duration> ttl = ttlFor(mapping.expiresAt(), now, maxTtl);
        if (ttl.isEmpty()) {
            return;
        }
        try {
            redis.opsForValue().set(PREFIX + mapping.shortCode(), mapping.longUrl(), ttl.get());
        } catch (RuntimeException e) {
            log.warn("Redirect cache write failed for {}: {}", mapping.shortCode(), e.getMessage());
        }
    }

    /**
     * The cache entry must never outlive the link, otherwise an expired link would keep
     * redirecting from cache. Empty means "don't cache" (already expired).
     */
    static Optional<Duration> ttlFor(Instant expiresAt, Instant now, Duration maxTtl) {
        if (expiresAt == null) {
            return Optional.of(maxTtl);
        }
        Duration remaining = Duration.between(now, expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return Optional.empty();
        }
        return Optional.of(remaining.compareTo(maxTtl) < 0 ? remaining : maxTtl);
    }
}
