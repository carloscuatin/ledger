package com.carloscuatin.ledgerapp.ledger.adapters.in.web;

import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.carloscuatin.ledger.contract.v1.api.TransfersApi;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * The version of the API lives in the first segment of the path, where a firewall and the access logs can see it. The
 * generated interfaces know their routes relative to the base of the contract, so the prefix is added here, and each of
 * their mappings declares the version it belongs to.
 */
@Configuration(proxyBeanMethods = false)
@Profile("api")
class WebConfiguration implements WebMvcConfigurer {

    /** Far more than any body of this contract needs, and little enough to be cheap to refuse. */
    private static final int MAX_BODY_BYTES = 16 * 1024;

    /** The one spelling of a version. Without it, Spring reads anything in that segment as a version. */
    private static final String VERSION = "v[1-9][0-9]*";

    /**
     * A field declared as a string takes only a JSON string. By default Jackson turns a number into its text, so a client
     * that sends an amount as a number, already rounded by its own parser, would be accepted as if it had been exact.
     */
    @Bean
    JsonMapperBuilderCustomizer onlyAStringIsAString() {
        return builder -> builder.withCoercionConfig(LogicalType.Textual, config -> {
            config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
    }

    /**
     * The server sets no limit on a JSON body. The parser counts what it reads, so this holds for a declared length and
     * for a chunked body alike, and it stops reading: what would otherwise reach validation, and the log, whole, is cut
     * here.
     */
    @Bean
    JsonFactoryBuilderCustomizer boundedBody() {
        return builder -> builder
                .streamReadConstraints(StreamReadConstraints.builder().maxDocumentLength(MAX_BODY_BYTES).build());
    }

    /**
     * The same field twice has no single meaning: a parser that reads in order keeps the last, and whatever looked at the
     * body before this application, like a gateway or a signature, may have believed the first. This names only reading:
     * writing has its own feature of the same name, which stays off, because what this application writes comes from
     * records, which cannot repeat a field.
     */
    @Bean
    JsonFactoryBuilderCustomizer noRepeatedField() {
        return builder -> builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
    }

    /** First of all, because what it protects from is what the filters ahead of the security chain do with a path. */
    @Bean
    FilterRegistrationBean<PathParameterFilter> pathParameterFilter() {
        var registration = new FilterRegistrationBean<>(new PathParameterFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

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
