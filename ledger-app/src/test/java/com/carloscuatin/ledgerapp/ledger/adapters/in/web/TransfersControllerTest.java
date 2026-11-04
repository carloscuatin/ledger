package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.carloscuatin.ledger.contract.v1.model.CreateTransferRequest;
import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.application.FindTransfer;
import com.carloscuatin.ledgerapp.ledger.application.ListTransfers;
import com.carloscuatin.ledgerapp.ledger.domain.TenantId;

class TransfersControllerTest {

    private final CreateTransfer createTransfer = mock();
    private final FindTransfer findTransfer = mock();
    private final ListTransfers listTransfers = mock();
    private final TransfersController controller = new TransfersController(createTransfer, findTransfer, listTransfers);

    @AfterEach
    void forgetThePrincipal() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(Optional<TenantId> tenant) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new TestPrincipal(tenant), null, List.of()));
    }

    /** ZZZ does not exist and XAU has no minor units: the JDK and the domain refuse each with a generic exception. */
    @ParameterizedTest
    @ValueSource(strings = {"ZZZ", "XAU"})
    void aCodeWithNoUsableCurrencyIsRefusedBeforeTheUseCase(String code) {
        var request = new CreateTransferRequest(UUID.randomUUID(), UUID.randomUUID(), "150000.00", code);

        assertThatThrownBy(() -> controller.createTransfer(request)).isInstanceOf(UnusableCurrencyException.class);
        verifyNoInteractions(createTransfer);
    }

    /** An amount is only judged in a currency that exists, whatever the order of the two fields in the body. */
    @Test
    void anUnusableCurrencyIsReportedEvenWithAnInvalidAmount() {
        var request = new CreateTransferRequest(UUID.randomUUID(), UUID.randomUUID(), "10.005", "ZZZ");

        assertThatThrownBy(() -> controller.createTransfer(request)).isInstanceOf(UnusableCurrencyException.class);
    }

    /** A use case opens a transaction, so a cursor that says nothing must not take a connection to find that out. */
    @Test
    void anInvalidCursorIsRefusedBeforeTheUseCase() {
        authenticate(Optional.of(new TenantId(UUID.randomUUID())));

        assertThatThrownBy(() -> controller.listTransfers("not-a-cursor", 1))
                .isInstanceOf(InvalidCursorException.class);
        verifyNoInteractions(listTransfers);
    }

    /** Having no tenant is the first thing wrong with such a request, whatever else it says. */
    @Test
    void aPrincipalWithoutATenantIsToldSoBeforeAboutItsCursor() {
        authenticate(Optional.empty());

        assertThatThrownBy(() -> controller.listTransfers("not-a-cursor", 1))
                .isInstanceOf(TenantRequiredException.class);
        verifyNoInteractions(listTransfers);
    }
}
