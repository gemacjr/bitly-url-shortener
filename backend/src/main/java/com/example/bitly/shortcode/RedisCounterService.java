package com.example.bitly.shortcode;

import com.example.bitly.config.AppProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis is single-threaded, so INCRBY is atomic across every write-service instance:
 * two instances can never receive overlapping ranges.
 */
@Component
public class RedisCounterService implements CounterService {

    private final StringRedisTemplate redis;
    private final String key;

    public RedisCounterService(StringRedisTemplate redis, AppProperties props) {
        this.redis = redis;
        this.key = props.counter().key();
    }

    @Override
    public long reserveBatch(int size) {
        Long end = redis.opsForValue().increment(key, size);
        if (end == null) {
            throw new IllegalStateException("Redis INCRBY returned null (pipeline/transaction?)");
        }
        return end - size; // INCRBY returns the new value, i.e. one past the end of our range
    }
}
