package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import java.security.Principal;
import java.util.Optional;

import com.carloscuatin.ledgerapp.ledger.domain.TenantId;

/**
 * Who an authenticated request acts for, and the only place a request's tenant comes from. Whatever verifies a
 * credential produces it, so nothing the caller sends can choose the tenant. An authenticated principal may belong to no
 * tenant, which is why the absence is part of the answer. Extending {@link Principal} also leaves more than one abstract
 * method, so it cannot be written as a lambda, which has no class of its own for an architecture rule to see.
 */
public interface TenantPrincipal extends Principal {

    Optional<TenantId> tenant();
}
