package com.example.bitly.shortcode;

/** Hands out disjoint ranges of a global, monotonically increasing counter. */
public interface CounterService {

    /**
     * Atomically reserves {@code size} consecutive values.
     *
     * @return the first value of the reserved range; the range is {@code [start, start + size)}
     */
    long reserveBatch(int size);
}
