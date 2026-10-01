package com.example.bitly.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bitly.common.ApiException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlRulesTest {

    private static final URI SHORT_DOMAIN = URI.create("http://localhost:8090");

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "http://example.com/a/b?c=d#e", "HTTPS://Example.com", "http://localhost:3001/x"})
    void acceptsHttpUrls(String url) {
        assertThat(UrlRules.validateLongUrl(url, SHORT_DOMAIN)).isEqualTo(url);
    }

    @Test
    void trimsWhitespace() {
        assertThat(UrlRules.validateLongUrl("  https://example.com  ", SHORT_DOMAIN)).isEqualTo("https://example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.com", "ftp://example.com", "javascript:alert(1)", "https://", "http://exa mple.com",
            "http://localhost:8090/abc1234"})
    void rejectsInvalidUnsafeOrSelfReferencingUrls(String url) {
        assertThatThrownBy(() -> UrlRules.validateLongUrl(url, SHORT_DOMAIN)).isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"my-link", "Promo_2030", "abc"})
    void acceptsValidAliases(String alias) {
        assertThatCode(() -> UrlRules.validateAlias(alias)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "has space", "slash/es", "emoji😀", "urls", "Actuator",
            "a-very-long-alias-that-is-way-over-the-limit"})
    void rejectsInvalidOrReservedAliases(String alias) {
        assertThatThrownBy(() -> UrlRules.validateAlias(alias)).isInstanceOf(ApiException.class);
    }
}
