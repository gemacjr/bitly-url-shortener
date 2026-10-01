package com.example.bitly.shortcode;

import com.example.bitly.config.AppProperties;
import java.math.BigInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * Counter + base62 short codes with counter batching (hellointerview "Counter Batching").
 *
 * <p>Raw counters give predictable, enumerable codes (0000001, 0000002, ...). We map each counter
 * value through an affine permutation {@code (n * MULTIPLIER + OFFSET) mod 62^7}. Because MULTIPLIER
 * is coprime to 62 the map is a bijection, so distinct counters still give distinct codes, but
 * consecutive links no longer look consecutive. This is obfuscation, not security.
 */
@Component
public class ShortCodeGenerator {

    public static final int CODE_LENGTH = 7;
    static final long SPACE = pow(62, CODE_LENGTH); // 3,521,614,606,208 codes

    private static final BigInteger MOD = BigInteger.valueOf(SPACE);
    private static final BigInteger MULTIPLIER = BigInteger.valueOf(2_147_483_647L); // prime, coprime to 62
    private static final BigInteger OFFSET = BigInteger.valueOf(1_234_567_890_123L);

    private final CounterService counter;
    private final int batchSize;
    private final ReentrantLock lock = new ReentrantLock(); // virtual-thread friendly, unlike synchronized

    private long next;
    private long end; // exclusive; next == end means the batch is used up

    public ShortCodeGenerator(CounterService counter, AppProperties props) {
        this.counter = counter;
        this.batchSize = props.counter().batchSize();
    }

    public String nextCode() {
        return encode(nextCounterValue());
    }

    long nextCounterValue() {
        lock.lock();
        try {
            if (next == end) {
                next = counter.reserveBatch(batchSize);
                end = next + batchSize;
            }
            return next++;
        } finally {
            lock.unlock();
        }
    }

    static String encode(long counterValue) {
        if (counterValue < 0 || counterValue >= SPACE) {
            throw new IllegalStateException("counter " + counterValue + " is outside the 7-char code space");
        }
        long scrambled = BigInteger.valueOf(counterValue).multiply(MULTIPLIER).add(OFFSET).mod(MOD).longValueExact();
        return Base62.encode(scrambled, CODE_LENGTH);
    }

    private static long pow(long base, int exp) {
        long result = 1;
        for (int i = 0; i < exp; i++) {
            result = Math.multiplyExact(result, base);
        }
        return result;
    }
}
