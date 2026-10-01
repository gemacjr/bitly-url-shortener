package com.example.bitly.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.bitly.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class UrlCacheTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final Duration MAX = Duration.ofHours(24);

    @Test
    void neverExpiringLinksUseMaxTtl() {
        assertThat(UrlCache.ttlFor(null, NOW, MAX)).contains(MAX);
    }

    @Test
    void ttlIsCappedByTheLinksOwnExpiration() {
        assertThat(UrlCache.ttlFor(NOW.plusSeconds(90), NOW, MAX)).contains(Duration.ofSeconds(90));
        assertThat(UrlCache.ttlFor(NOW.plus(Duration.ofDays(30)), NOW, MAX)).contains(MAX);
    }

    @Test
    void expiredLinksAreNotCached() {
        assertThat(UrlCache.ttlFor(NOW, NOW, MAX)).isEmpty();
        assertThat(UrlCache.ttlFor(NOW.minusSeconds(1), NOW, MAX)).isEmpty();
    }

    @Test
    void redisOutageIsACacheMissNotAnError() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        UrlCache cache = new UrlCache(redis, new AppProperties("http://sho.rt", null, null, new AppProperties.Cache(MAX)));

        assertThat(cache.get("abc1234")).isEmpty();
        cache.put(new UrlMapping("abc1234", "https://example.com", NOW, null, false), NOW); // must not throw
    }
}
