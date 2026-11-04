package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.carloscuatin.ledgerapp.ledger.application.TransferQueries;
import com.carloscuatin.ledgerapp.ledger.application.TransferView;
import com.carloscuatin.ledgerapp.ledger.domain.AccountId;
import com.carloscuatin.ledgerapp.ledger.domain.Money;
import com.carloscuatin.ledgerapp.ledger.domain.TransferId;
import com.carloscuatin.ledgerapp.ledger.domain.TransferStatus;
import com.carloscuatin.ledgerapp.ledger.domain.TransferTerms;

/**
 * Reads transfers with SQL, as plain values: no session keeps what it returns, and the columns it selects are all a read
 * needs. {@link JdbcClient} goes through the connection of the running transaction, so it shares the tenant that was set
 * on that connection when the transaction began. The statements name no tenant, because row level security adds it.
 */
class JdbcTransferQueries implements TransferQueries {

    private static final String FIND = """
            SELECT id, source_account_id, destination_account_id, amount_minor, currency, status
            FROM transfer
            WHERE id = ?
            """;

    private final JdbcClient jdbc;

    JdbcTransferQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Outside a transaction no tenant is set, and row level security would show no rows instead of failing. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TransferView> find(TransferId id) {
        return jdbc.sql(FIND).param(id.value()).query(JdbcTransferQueries::view).optional();
    }

    /**
     * Every status the schema allows is a state a transfer can be in. A currency it allows and the JDK does not know, or
     * one without minor units, has no amount to show: the failure is reported as one, since any amount would be invented.
     */
    private static TransferView view(ResultSet row, int number) throws SQLException {
        var money = Money.ofMinor(row.getLong("amount_minor"), Currency.getInstance(row.getString("currency")));
        var terms = new TransferTerms(new AccountId(row.getObject("source_account_id", UUID.class)),
                new AccountId(row.getObject("destination_account_id", UUID.class)), money);
        return new TransferView(new TransferId(row.getObject("id", UUID.class)), terms,
                TransferStatus.valueOf(row.getString("status")));
    }
}
