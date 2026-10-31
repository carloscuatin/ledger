package com.carloscuatin.ledgerapp.ledger.adapters.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Currency;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LedgerConfigurationTest {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Currency USD = Currency.getInstance("USD");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LedgerConfiguration.class);

    @Test
    void theEnabledCurrenciesBindFromTheirProperty() {
        runner.withPropertyValues("ledger.currencies=COP,USD").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LedgerProperties.class).currencies()).containsExactlyInAnyOrder(COP, USD);
        });
    }

    @Test
    void startupFailsWithoutTheProperty() {
        runner.run(
                context -> assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("currencies"));
    }

    @Test
    void startupFailsWithAnEmptyProperty() {
        runner.withPropertyValues("ledger.currencies=")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("currencies"));
    }

    @Test
    void startupFailsWithAnUnknownCurrencyCode() {
        runner.withPropertyValues("ledger.currencies=COP,XYZ")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("ledger.currencies"));
    }

    @Test
    void startupFailsWithALowerCaseCurrencyCode() {
        runner.withPropertyValues("ledger.currencies=cop")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("ledger.currencies"));
    }

    @Test
    void startupFailsWithACurrencyWithoutAScale() {
        runner.withPropertyValues("ledger.currencies=COP,XAU").run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasStackTraceContaining("every enabled currency needs a scale"));
    }
}
