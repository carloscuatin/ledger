package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Currency;

/**
 * The domain accepts any currency with a scale; a deployment operates only the currencies it enables, so a transfer in
 * any other is refused.
 */
public class CurrencyNotEnabledException extends DomainException {

    public CurrencyNotEnabledException(Currency currency) {
        super("Currency not enabled: " + currency.getCurrencyCode());
    }
}
