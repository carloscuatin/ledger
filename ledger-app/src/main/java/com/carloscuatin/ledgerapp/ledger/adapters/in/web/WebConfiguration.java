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

    /** The one spelling of a version. Without it, Spring reads anything in that segment as a version. */
    private static final String VERSION = "v[1-9][0-9]*";

    /**
     * Only a path that names a version is read as one: the first segment of a path outside the API, like the error page,
     * is not a version that happens to be invalid. Such a path has no version at all, and that must not be an error.
     */
    @Override
    public void configureApiVersioning(ApiVersionConfigurer versioning) {
        versioning.usePathSegment(0, path -> path.value().matches("/" + VERSION + "(/.*)?"))
                .setVersionRequired(false);
    }

    /** The pattern and not a bare variable, or every spelling Spring can parse as the same version would be an alias. */
    @Override
    public void configurePathMatch(PathMatchConfigurer paths) {
        paths.addPathPrefix("/{version:" + VERSION + "}", HandlerTypePredicate.forAssignableType(TransfersApi.class));
    }
}
