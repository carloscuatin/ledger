package com.carloscuatin.ledgerapp.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TransferIdTest {

    @Test
    void transferReferencesWithTheSameUuidAreEqual() {
        var uuid = UUID.randomUUID();
        var transfer = new TransferId(uuid);
        var same = new TransferId(uuid);
        var other = new TransferId(UUID.randomUUID());

        assertThat(transfer).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other);
    }

    @Test
    void aNullTransferIdentifierIsRejected() {
        assertThatThrownBy(() -> new TransferId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value");
    }
}
