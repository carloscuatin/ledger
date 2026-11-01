package com.carloscuatin.ledgerapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.carloscuatin.ledgerapp.ledger.adapters.config.LedgerProperties;
import com.carloscuatin.ledgerapp.ledger.adapters.out.persistence.LedgerDatabase;
import com.carloscuatin.ledgerapp.ledger.application.CreateTransfer;
import com.carloscuatin.ledgerapp.ledger.domain.AccountId;
import com.carloscuatin.ledgerapp.ledger.domain.CurrencyNotEnabledException;
import com.carloscuatin.ledgerapp.ledger.domain.Money;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

/**
 * Starts the whole application the way it runs in production: component scanning, auto-configuration, the packaged
 * properties and its own database role, with the connection settings coming from outside. Slower than a context runner,
 * so there is only this one.
 */
@SpringBootTest
class LedgerApplicationIT {

    private static final Currency COP = Currency.getInstance("COP");
    private static final AccountId SOURCE = new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000001"));
    private static final AccountId DESTINATION = new AccountId(UUID.fromString("00000000-0000-7000-8000-000000000002"));

    @DynamicPropertySource
    static void connectAsTheApplication(DynamicPropertyRegistry registry) {
        LedgerDatabase.connectAsTheApplication(registry);
    }

    @Autowired
    private Environment environment;

    @Autowired
    private LedgerProperties properties;

    @Autowired
    private CreateTransfer createTransfer;

    @Test
    void thePackagedSettingsEnableOnlyPesos() {
        assertThat(properties.currencies()).containsExactly(COP);
    }

    @Test
    void theApplicationRunsWithoutAProfileAndOffersTheUseCase() {
        var pesos = new TransferTerms(SOURCE, DESTINATION, Money.parse("150000.00", COP));
        var dollars = new TransferTerms(SOURCE, DESTINATION, Money.parse("100.00", Currency.getInstance("USD")));

        assertThat(environment.getActiveProfiles()).isEmpty();
        assertThat(createTransfer.create(pesos).terms()).isEqualTo(pesos);
        assertThatThrownBy(() -> createTransfer.create(dollars)).isInstanceOf(CurrencyNotEnabledException.class);
    }
}
