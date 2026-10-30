package com.carloscuatin.ledgerapp.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class AccountIdTest {
    @Test
    void accountReferencesWithTheSameUuidAreEqual() {
        var uuid = UUID.randomUUID();
        var account = new AccountId(uuid);
        var same = new AccountId(uuid);
        var other = new AccountId(UUID.randomUUID());

        assertThat(account).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other);
    }

    @Test
    void aNullAccountIdentifierIsRejected() {
        assertThatThrownBy(() -> new AccountId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value");
    }
}
