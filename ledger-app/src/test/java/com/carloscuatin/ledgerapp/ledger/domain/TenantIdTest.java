package com.carloscuatin.ledgerapp.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TenantIdTest {

    @Test
    void tenantReferencesWithTheSameUuidAreEqual() {
        var uuid = UUID.randomUUID();
        var tenant = new TenantId(uuid);
        var same = new TenantId(uuid);
        var other = new TenantId(UUID.randomUUID());

        assertThat(tenant).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other);
    }

    @Test
    void aNullTenantIdentifierIsRejected() {
        assertThatThrownBy(() -> new TenantId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value");
    }
}
