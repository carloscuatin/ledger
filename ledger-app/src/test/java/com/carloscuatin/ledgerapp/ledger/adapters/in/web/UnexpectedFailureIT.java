package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import static com.carloscuatin.ledgerapp.ledger.adapters.in.web.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.domain.CurrencyMismatchException;

/**
 * Two readers want opposite things from a failure of this side: operators need all of it in the log, and whoever sent
 * the request must get none of it.
 */
@ExtendWith(OutputCaptureExtension.class)
class UnexpectedFailureIT extends AuthenticatedApi {

    private static final String TRANSFERS = "/v1/transfers";
    private static final String MARK = "SYNTHETICMARK";

    @MockitoBean
    private CreateTransfer createTransfer;

    @Test
    void aFailingUseCaseRevealsNothingAndIsLoggedWithItsTrace(CapturedOutput output) throws Exception {
        when(createTransfer.create(any())).thenThrow(new IllegalStateException("SELECT secret FROM " + MARK));

        var response = send(postingJson(requestAs(UUID.randomUUID(), TRANSFERS), aValidBody()));

        assertProblem(response, ProblemType.INTERNAL_ERROR, TRANSFERS);
        assertThat(response.body()).doesNotContain(MARK).doesNotContain("IllegalStateException");
        assertThat(output).contains(" ERROR ").contains("java.lang.IllegalStateException: SELECT secret FROM " + MARK)
                .contains("at " + getClass().getPackageName());
    }

    /** A rule of the domain the API has no answer for is a mistake in this code, not something a client can correct. */
    @Test
    void aDomainRuleTheApiNeverExpectsIsAFailureOfThisSide(CapturedOutput output) throws Exception {
        var failure = new CurrencyMismatchException(Currency.getInstance("COP"), Currency.getInstance("USD"));
        when(createTransfer.create(any())).thenThrow(failure);

        var response = send(postingJson(requestAs(UUID.randomUUID(), TRANSFERS), aValidBody()));

        assertProblem(response, ProblemType.INTERNAL_ERROR, TRANSFERS);
        assertThat(response.body()).doesNotContain("USD");
        assertThat(output).contains(" ERROR ").contains(CurrencyMismatchException.class.getName());
    }
}
