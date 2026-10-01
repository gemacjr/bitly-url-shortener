package com.example.bitly.shortcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bitly.config.AppProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

    private static AppProperties props(int batchSize) {
        return new AppProperties("http://sho.rt", null, new AppProperties.Counter("k", batchSize), null);
    }

    /** In-memory stand-in with the same contract as Redis INCRBY. */
    private static CounterService inMemoryCounter() {
        AtomicLong value = new AtomicLong();
        return size -> value.addAndGet(size) - size;
    }

    @Test
    void usesOneCounterRoundTripPerBatch() {
        CounterService counter = mock(CounterService.class);
        when(counter.reserveBatch(3)).thenReturn(100L, 500L);
        ShortCodeGenerator generator = new ShortCodeGenerator(counter, props(3));

        List<Long> values = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            values.add(generator.nextCounterValue());
        }

        assertThat(values).containsExactly(100L, 101L, 102L, 500L, 501L);
        verify(counter, times(2)).reserveBatch(3);
    }

    @Test
    void codesAreSevenBase62CharsAndNotSequentialLooking() {
        String a = ShortCodeGenerator.encode(1);
        String b = ShortCodeGenerator.encode(2);

        assertThat(a).hasSize(7).matches("[0-9a-zA-Z]{7}");
        assertThat(b).hasSize(7).matches("[0-9a-zA-Z]{7}");
        // Consecutive counters should not produce codes sharing a long common prefix.
        assertThat(a.substring(0, 4)).isNotEqualTo(b.substring(0, 4));
    }

    @Test
    void scrambleIsInjective() {
        Set<String> codes = new HashSet<>();
        for (long i = 0; i < 100_000; i++) {
            codes.add(ShortCodeGenerator.encode(i));
        }
        assertThat(codes).hasSize(100_000);
    }

    @Test
    void edgesOfCodeSpace() {
        assertThat(ShortCodeGenerator.encode(ShortCodeGenerator.SPACE - 1)).hasSize(7);
        assertThatThrownBy(() -> ShortCodeGenerator.encode(ShortCodeGenerator.SPACE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concurrentCallersNeverGetTheSameCode() throws Exception {
        ShortCodeGenerator generator = new ShortCodeGenerator(inMemoryCounter(), props(10));
        Set<String> codes = ConcurrentHashMap.newKeySet();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int t = 0; t < 16; t++) {
            tasks.add(() -> {
                for (int i = 0; i < 1_000; i++) {
                    codes.add(generator.nextCode());
                }
                return null;
            });
        }
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var f : pool.invokeAll(tasks)) {
                f.get();
            }
        }
        assertThat(codes).hasSize(16_000);
    }

    @Test
    void twoInstancesSharingOneCounterNeverCollide() {
        CounterService shared = inMemoryCounter();
        ShortCodeGenerator east = new ShortCodeGenerator(shared, props(7));
        ShortCodeGenerator west = new ShortCodeGenerator(shared, props(7));
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            codes.add(east.nextCode());
            codes.add(west.nextCode());
        }
        assertThat(codes).hasSize(1_000);
    }
}
