package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class UuidV7IdGeneratorTest {

    private static final int RFC_9562_VARIANT = 2;
    private static final int TIMESTAMP_SHIFT = 16;
    private static final int RUN = 10_000;

    private final UuidV7IdGenerator ids = new UuidV7IdGenerator();

    @Test
    void everyIdentifierIsVersionSevenWithTheRfcVariant() {
        assertThat(Stream.generate(ids::next).limit(RUN)).allSatisfy(uuid -> {
            assertThat(uuid.version()).isEqualTo(7);
            assertThat(uuid.variant()).isEqualTo(RFC_9562_VARIANT);
        });
    }

    @Test
    void theTopBitsCarryTheMomentOfCreation() {
        var before = System.currentTimeMillis();
        var uuid = ids.next();
        var after = System.currentTimeMillis();

        assertThat(uuid.getMostSignificantBits() >>> TIMESTAMP_SHIFT).isBetween(before, after);
    }

    @Test
    void tenThousandInARowDoNotRepeat() {
        var seen = new HashSet<UUID>();

        for (var i = 0; i < RUN; i++) {
            seen.add(ids.next());
        }

        assertThat(seen).hasSize(RUN);
    }
}
