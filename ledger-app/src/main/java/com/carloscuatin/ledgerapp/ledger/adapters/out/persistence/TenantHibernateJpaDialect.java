package com.carloscuatin.ledgerapp.ledger.adapters.out.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import org.hibernate.Session;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.TransactionDefinition;

import com.carloscuatin.ledgerapp.ledger.application.CurrentTenant;
import com.carloscuatin.ledgerapp.ledger.domain.TenantId;

import jakarta.persistence.EntityManager;

/**
 * Carries the tenant of the current scope into every new transaction, on the connection that transaction holds. Every
 * way of starting a transaction goes through here, and the transaction manager cleans up after any failure in this
 * method, which it does not do for code that runs after the transaction has begun.
 */
final class TenantHibernateJpaDialect extends HibernateJpaDialect {

    private static final long serialVersionUID = 1L;

    @Override
    public Object beginTransaction(EntityManager entityManager, TransactionDefinition definition)
            throws SQLException {
        // Before beginning: refusing here never asks the pool for a connection.
        var tenant = CurrentTenant.require();
        var transactionData = super.beginTransaction(entityManager, definition);
        entityManager.unwrap(Session.class).doWork(connection -> applyTenant(connection, tenant));
        return transactionData;
    }

    private static void applyTenant(Connection connection, TenantId tenant) throws SQLException {
        // The third argument limits the setting to this transaction, so it ends with the commit or the rollback and
        // the connection goes back to the pool without a tenant. SET LOCAL does the same but takes no parameters.
        try (var statement = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            statement.setString(1, tenant.value().toString());
            statement.execute();
        }
    }
}
