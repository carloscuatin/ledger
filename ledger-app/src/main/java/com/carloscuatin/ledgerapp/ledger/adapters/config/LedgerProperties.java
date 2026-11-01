package com.carloscuatin.ledgerapp.ledger.adapters.config;

import java.util.Currency;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;

/**
 * Settings of the ledger module, bound from the {@code ledger.*} properties or their environment variables. They are
 * validated when the application starts, so a wrong setting stops the startup instead of the first request.
 *
 * <p>
 * The rule that needs code is a {@link Validator} rather than a Bean Validation method, because a rejection tied to the
 * field makes the startup report name the setting the operator has to change.
 *
 * @param currencies the currencies this deployment operates; each tenant's are a subset
 */
@ConfigurationProperties("ledger")
@Validated
public record LedgerProperties(@NotEmpty Set<Currency> currencies) implements Validator {

    @Override
    public boolean supports(Class<?> type) {
        return LedgerProperties.class.isAssignableFrom(type);
    }

    /** A currency without a scale cannot hold money in minor units. */
    @Override
    public void validate(Object target, Errors errors) {
        // A missing property is reported by @NotEmpty; this rule only looks at the currencies that are there.
        if (currencies != null && currencies.stream().anyMatch(currency -> currency.getDefaultFractionDigits() < 0)) {
            errors.rejectValue("currencies", "scale", "every enabled currency needs a scale");
        }
    }
}
