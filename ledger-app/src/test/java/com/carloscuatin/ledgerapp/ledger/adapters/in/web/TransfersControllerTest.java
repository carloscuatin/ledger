package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.carloscuatin.ledger.contract.v1.model.CreateTransferRequest;
import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.application.FindTransfer;

class TransfersControllerTest {

    private final CreateTransfer createTransfer = mock();
    private final FindTransfer findTransfer = mock();
    private final TransfersController controller = new TransfersController(createTransfer, findTransfer);

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
}
