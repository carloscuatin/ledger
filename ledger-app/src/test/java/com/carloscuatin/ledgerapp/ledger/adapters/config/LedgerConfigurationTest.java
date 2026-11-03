package com.carloscuatin.ledgerapp.ledger.adapters.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.application.IdGenerator;
import com.carloscuatin.ledgerapp.ledger.application.Transfers;
import com.carloscuatin.ledgerapp.ledger.domain.AccountId;
import com.carloscuatin.ledgerapp.ledger.domain.CurrencyNotEnabledException;
import com.carloscuatin.ledgerapp.ledger.domain.Money;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

class LedgerConfigurationTest {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Currency USD = Currency.getInstance("USD");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LedgerConfiguration.class)
            .withBean(IdGenerator.class, () -> UUID::randomUUID)
            .withBean(Transfers.class, () -> hold -> {
            });

    @Test
    void theEnabledCurrenciesBindFromTheirProperty() {
        runner.withPropertyValues("ledger.currencies=COP,USD").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LedgerProperties.class).currencies()).containsExactlyInAnyOrder(COP, USD);
        });
    }

    @Test
    void startupFailsWithoutTheProperty() {
        runner.withInitializer(environmentVariables(Map.of())).run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasStackTraceContaining("ledger.currencies"));
    }

    @Test
    void startupFailsWithAnEmptyProperty() {
        runner.withPropertyValues("ledger.currencies=")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("ledger.currencies"));
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
                .hasStackTraceContaining("every enabled currency needs a scale")
                .hasStackTraceContaining("ledger.currencies"));
    }

    @Test
    void currenciesWithoutDecimalsHaveAScale() {
        runner.withPropertyValues("ledger.currencies=JPY,BHD").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void theContainerBuildsTheUseCaseWithTheEnabledCurrencies() {
        var source = new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000001"));
        var destination = new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000002"));
        var pesos = new TransferTerms(source, destination, Money.parse("150000.00", COP));
        var dollars = new TransferTerms(source, destination, Money.parse("100.00", USD));

        runner.withPropertyValues("ledger.currencies=COP").run(context -> {
            assertThat(context).hasSingleBean(CreateTransfer.class);
            var createTransfer = context.getBean(CreateTransfer.class);
            assertThat(createTransfer.create(pesos).terms()).isEqualTo(pesos);
            assertThatThrownBy(() -> createTransfer.create(dollars)).isInstanceOf(CurrencyNotEnabledException.class);
        });
    }

    @Test
    void enablingACurrencyIsOnlyConfiguration() {
        var source = new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000001"));
        var destination = new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000002"));
        var dollars = new TransferTerms(source, destination, Money.parse("100.00", USD));

        runner.withPropertyValues("ledger.currencies=COP,USD").run(context -> {
            var createTransfer = context.getBean(CreateTransfer.class);
            assertThat(createTransfer.create(dollars).terms()).isEqualTo(dollars);
        });
    }

    @Test
    void anEnvironmentVariableBindsToTheProperty() {
        runner.withInitializer(environmentVariables(Map.of("LEDGER_CURRENCIES", "COP,USD"))).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LedgerProperties.class).currencies()).containsExactlyInAnyOrder(COP, USD);
        });
    }

    /** Replaces the operating system variables the context sees, the way a container platform sets them. */
    private static ApplicationContextInitializer<ConfigurableApplicationContext> environmentVariables(
            Map<String, Object> variables) {
        var name = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
        return context -> context.getEnvironment()
                .getPropertySources()
                .replace(name, new SystemEnvironmentPropertySource(name, variables));
    }
}
