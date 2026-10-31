package com.carloscuatin.ledgerapp.ledger.adapters.config;

import java.util.Currency;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;

/**
 * Settings of the ledger module, bound from the {@code ledger.*} properties or their environment variables. They are
 * validated when the application starts, so a wrong setting stops the startup instead of the first request.
 *
 * @param currencies the currencies this deployment operates; each tenant's are a subset
 */
@ConfigurationProperties("ledger")
@Validated
public record LedgerProperties(@NotEmpty Set<Currency> currencies) {

    /** A currency without a scale cannot hold money in minor units. */
    @AssertTrue(message = "every enabled currency needs a scale")
    boolean isEveryCurrencyScaled() {
        // A missing property is reported by @NotEmpty; this rule only looks at the currencies that are there.
        return currencies == null || currencies.stream().allMatch(currency -> currency.getDefaultFractionDigits() >= 0);
    }
}
