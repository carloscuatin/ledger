package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import java.util.Currency;
import java.util.Optional;
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
import com.carloscuatin.ledgerapp.ledger.application.FindTransfer;
import com.carloscuatin.ledgerapp.ledger.application.ListTransfers;
import com.carloscuatin.ledgerapp.ledger.application.TransferView;
import com.carloscuatin.ledgerapp.ledger.domain.AccountId;
import com.carloscuatin.ledgerapp.ledger.domain.CurrencyWithoutScaleException;
import com.carloscuatin.ledgerapp.ledger.domain.Money;
import com.carloscuatin.ledgerapp.ledger.domain.PendingTransfer;
import com.carloscuatin.ledgerapp.ledger.domain.TransferId;
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
    private final FindTransfer findTransfer;
    private final ListTransfers listTransfers;

    TransfersController(CreateTransfer createTransfer, FindTransfer findTransfer, ListTransfers listTransfers) {
        this.createTransfer = createTransfer;
        this.findTransfer = findTransfer;
        this.listTransfers = listTransfers;
    }

    @Override
    public ResponseEntity<Transfer> createTransfer(CreateTransferRequest request) {
        var terms = toTerms(request);

        var hold = AuthenticatedTenant.call(() -> createTransfer.create(terms));

        // A path and not a full URL: building one from the request would repeat the Host header the client chose.
        var location = UriComponentsBuilder.fromPath(VERSION_PREFIX).path(PATH_GET_TRANSFER).build(hold.id().value());
        return ResponseEntity.created(location).body(toContract(hold));
    }

    @Override
    public ResponseEntity<Transfer> getTransfer(UUID id) {
        var view = AuthenticatedTenant.call(() -> findTransfer.find(new TransferId(id)));

        return ResponseEntity.ok(toContract(view));
    }

    @Override
    public ResponseEntity<TransferPage> listTransfers(@Nullable String cursor, Integer limit) {
        // Decoded inside the scope, so that a principal without a tenant is told that first, whatever its cursor says.
        var page = AuthenticatedTenant
                .call(() -> listTransfers.list(Optional.ofNullable(cursor).map(Cursor::decode), limit));

        var items = page.items().stream().map(TransfersController::toContract).toList();
        return ResponseEntity.ok(new TransferPage(items, page.next().map(Cursor::encode).orElse(null)));
    }

    private static TransferTerms toTerms(CreateTransferRequest request) {
        var source = new AccountId(request.sourceAccountId());
        var destination = new AccountId(request.destinationAccountId());
        var amount = parseAmount(request.amount(), currencyOf(request.currency()));
        return new TransferTerms(source, destination, amount);
    }

    /**
     * A currency without a scale is the client's to fix only here, where the client chose it. Translated at this call and
     * not by a handler, because the same exception from a stored row is a failure of this side and has to stay one.
     */
    private static Money parseAmount(String amount, Currency currency) {
        try {
            return Money.parse(amount, currency);
        } catch (CurrencyWithoutScaleException _) {
            throw new UnusableCurrencyException();
        }
    }

    /** The contract fixes the shape of the code, not that it names a currency. */
    private static Currency currencyOf(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException _) {
            throw new UnusableCurrencyException();
        }
    }

    private static Transfer toContract(PendingTransfer hold) {
        return toContract(new TransferView(hold.id(), hold.terms(), hold.status()));
    }

    private static Transfer toContract(TransferView view) {
        var terms = view.terms();
        var id = view.id().value();
        var source = terms.source().value();
        var destination = terms.destination().value();
        var amount = terms.amount().toDecimalString();
        var currency = terms.amount().currency().getCurrencyCode();
        var status = TransferStatus.fromValue(view.status().name());
        return new Transfer(id, source, destination, amount, currency, status);
    }
}
