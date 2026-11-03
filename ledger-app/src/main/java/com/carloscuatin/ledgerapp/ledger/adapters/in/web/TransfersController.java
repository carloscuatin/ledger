package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import java.util.Currency;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.carloscuatin.ledger.contract.v1.api.TransfersApi;
import com.carloscuatin.ledger.contract.v1.model.CreateTransferRequest;
import com.carloscuatin.ledger.contract.v1.model.Transfer;
import com.carloscuatin.ledger.contract.v1.model.TransferPage;
import com.carloscuatin.ledger.contract.v1.model.TransferStatus;
import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.domain.AccountId;
import com.carloscuatin.ledgerapp.ledger.domain.Money;
import com.carloscuatin.ledgerapp.ledger.domain.PendingTransfer;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

/**
 * The transfer operations of the contract. Routes, status codes and validation come from the generated interface; this
 * class translates between the contract's types and the domain's. The generated signatures take no tenant, so a request
 * has no way to pass one.
 */
@RestController
@Profile("api")
class TransfersController implements TransfersApi {

    private static final String VERSION_PREFIX = "/v1";

    private final CreateTransfer createTransfer;

    TransfersController(CreateTransfer createTransfer) {
        this.createTransfer = createTransfer;
    }

    @Override
    public ResponseEntity<Transfer> createTransfer(CreateTransferRequest request) {
        var terms = new TransferTerms(new AccountId(request.sourceAccountId()),
                new AccountId(request.destinationAccountId()),
                Money.parse(request.amount(), currencyOf(request.currency())));

        var hold = AuthenticatedTenant.call(() -> createTransfer.create(terms));

        // A path and not a full URL: building one from the request would repeat the Host header the client chose.
        var location = UriComponentsBuilder.fromPath(VERSION_PREFIX).path(PATH_GET_TRANSFER).build(hold.id().value());
        return ResponseEntity.created(location).body(toContract(hold));
    }

    @Override
    public ResponseEntity<Transfer> getTransfer(UUID id) {
        throw new UnsupportedOperationException("Reading a transfer is not available");
    }

    @Override
    public ResponseEntity<TransferPage> listTransfers(@Nullable String cursor, Integer limit) {
        throw new UnsupportedOperationException("Listing transfers is not available");
    }

    /** The contract fixes the shape of the code, not that it names a currency with minor units. */
    private static Currency currencyOf(String code) {
        Currency currency;
        try {
            currency = Currency.getInstance(code);
        } catch (IllegalArgumentException _) {
            throw new UnusableCurrencyException();
        }
        if (currency.getDefaultFractionDigits() < 0) {
            throw new UnusableCurrencyException();
        }
        return currency;
    }

    private static Transfer toContract(PendingTransfer hold) {
        var terms = hold.terms();
        return new Transfer(hold.id().value(), terms.source().value(), terms.destination().value(),
                terms.amount().toDecimalString(), terms.amount().currency().getCurrencyCode(),
                TransferStatus.fromValue(hold.status().name()));
    }
}
