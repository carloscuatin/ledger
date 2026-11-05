package com.carloscuatin.ledgerapp.ledger.domain;

import java.util.Currency;

/**
 * An amount that cannot be interpreted. Its text may come from outside and be of any length, and an exception travels to
 * logs and error handlers, so this one keeps only the start of that text and its length. The whole text is not
 * referenced from here, so it can be collected while the exception lives on.
 */
public class InvalidAmountException extends DomainException {

    private static final int KEPT_CHARACTERS = 32;

    public InvalidAmountException(String amount, Currency currency) {
        super("Invalid amount %s for %s".formatted(bounded(amount), currency.getCurrencyCode()));
    }

    private static String bounded(String amount) {
        if (amount.length() <= KEPT_CHARACTERS) {
            return "'" + amount + "'";
        }
        return "'%s' (%d characters)".formatted(amount.substring(0, KEPT_CHARACTERS), amount.length());
    }
}
