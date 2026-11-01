package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import java.security.SecureRandom;
import java.util.UUID;

import com.carloscuatin.ledgerapp.ledger.application.IdGenerator;

/**
 * Builds time ordered identifiers as RFC 9562 defines version 7: 48 bits of Unix milliseconds, then the version, 12
 * random bits, the variant and 62 random bits. Starting with the time keeps new rows close together in an index.
 */
final class UuidV7IdGenerator implements IdGenerator {

    private static final int TIMESTAMP_SHIFT = 16;
    private static final long VERSION_7 = 0x7000L;
    private static final long RANDOM_12_BITS = 0x0FFFL;
    private static final long VARIANT_RFC = 0x8000_0000_0000_0000L;
    private static final long RANDOM_62_BITS = 0x3FFF_FFFF_FFFF_FFFFL;

    private final SecureRandom random = new SecureRandom();

    @Override
    public UUID next() {
        var high = (System.currentTimeMillis() << TIMESTAMP_SHIFT) | VERSION_7 | (random.nextLong() & RANDOM_12_BITS);
        var low = VARIANT_RFC | (random.nextLong() & RANDOM_62_BITS);
        return new UUID(high, low);
    }
}
