package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.carloscuatin.ledger.contract.v1.api.TransfersApi;

/**
 * The version of the API lives in the first segment of the path, where a firewall and the access logs can see it. The
 * generated interfaces know their routes relative to the base of the contract, so the prefix is added here, and each of
 * their mappings declares the version it belongs to.
 */
@Configuration(proxyBeanMethods = false)
@Profile("api")
class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer versioning) {
        versioning.usePathSegment(0);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer paths) {
        paths.addPathPrefix("/{version}", HandlerTypePredicate.forAssignableType(TransfersApi.class));
    }
}
