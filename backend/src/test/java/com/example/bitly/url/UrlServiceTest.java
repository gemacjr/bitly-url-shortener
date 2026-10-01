package com.example.bitly.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.bitly.common.ApiException;
import com.example.bitly.config.AppProperties;
import com.example.bitly.shortcode.ShortCodeGenerator;
import com.example.bitly.url.UrlDtos.ShortenRequest;
import com.example.bitly.url.UrlDtos.UrlResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final String LONG = "https://www.example.com/some/very/long/url";

    @Mock UrlRepository repository;
    @Mock UrlCache cache;
    @Mock ShortCodeGenerator generator;

    UrlService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("http://sho.rt/", null, null, null);
        service = new UrlService(repository, cache, generator, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---- shorten ----

    @Test
    void shorten_generatesCodeAndReturnsShortUrl() {
        when(generator.nextCode()).thenReturn("aB3xY9z");
        when(repository.saveIfAbsent(any())).thenReturn(true);

        UrlResponse res = service.shorten(new ShortenRequest(LONG, null, null));

        assertThat(res.shortUrl()).isEqualTo("http://sho.rt/aB3xY9z");
        assertThat(res.shortCode()).isEqualTo("aB3xY9z");
        assertThat(res.longUrl()).isEqualTo(LONG);
        assertThat(res.customAlias()).isFalse();
        assertThat(res.createdAt()).isEqualTo(NOW);
        assertThat(res.expirationDate()).isNull();
    }

    @Test
    void shorten_retriesWhenGeneratedCodeCollidesWithExistingAlias() {
        when(generator.nextCode()).thenReturn("promo01", "zzzzzzz");
        when(repository.saveIfAbsent(any())).thenReturn(false, true);

        assertThat(service.shorten(new ShortenRequest(LONG, null, null)).shortCode()).isEqualTo("zzzzzzz");
        verify(repository, times(2)).saveIfAbsent(any());
    }

    @Test
    void shorten_givesUpAfterMaxAttempts() {
        when(generator.nextCode()).thenReturn("taken00");
        when(repository.saveIfAbsent(any())).thenReturn(false);

        assertThatThrownBy(() -> service.shorten(new ShortenRequest(LONG, null, null)))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, times(UrlService.MAX_GENERATION_ATTEMPTS)).saveIfAbsent(any());
    }

    @Test
    void shorten_withCustomAlias_usesAliasAndSkipsGenerator() {
        when(repository.saveIfAbsent(any())).thenReturn(true);

        UrlResponse res = service.shorten(new ShortenRequest(LONG, " my-link ", null));

        assertThat(res.shortCode()).isEqualTo("my-link");
        assertThat(res.customAlias()).isTrue();
        verifyNoInteractions(generator);
    }

    @Test
    void shorten_blankAliasIsTreatedAsNoAlias() {
        when(generator.nextCode()).thenReturn("aB3xY9z");
        when(repository.saveIfAbsent(any())).thenReturn(true);

        assertThat(service.shorten(new ShortenRequest(LONG, "   ", null)).customAlias()).isFalse();
    }

    @Test
    void shorten_takenAlias_is409() {
        when(repository.saveIfAbsent(any())).thenReturn(false);

        assertThatThrownBy(() -> service.shorten(new ShortenRequest(LONG, "my-link", null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void shorten_persistsExpiration() {
        Instant expires = NOW.plus(Duration.ofDays(7));
        when(generator.nextCode()).thenReturn("aB3xY9z");
        when(repository.saveIfAbsent(any())).thenReturn(true);

        service.shorten(new ShortenRequest(LONG, null, expires));

        ArgumentCaptor<UrlMapping> saved = ArgumentCaptor.forClass(UrlMapping.class);
        verify(repository).saveIfAbsent(saved.capture());
        assertThat(saved.getValue().expiresAt()).isEqualTo(expires);
    }

    @Test
    void shorten_expirationInThePast_is400() {
        assertThatThrownBy(() -> service.shorten(new ShortenRequest(LONG, null, NOW)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(repository);
    }

    @Test
    void shorten_invalidUrl_is400() {
        assertThatThrownBy(() -> service.shorten(new ShortenRequest("not a url", null, null)))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(repository, generator);
    }

    // ---- resolve (redirect path) ----

    @Test
    void resolve_cacheHit_neverTouchesDynamo() {
        when(cache.get("aB3xY9z")).thenReturn(Optional.of(LONG));

        assertThat(service.resolve("aB3xY9z")).isEqualTo(LONG);
        verifyNoInteractions(repository);
    }

    @Test
    void resolve_cacheMiss_readsDynamoAndPopulatesCache() {
        UrlMapping mapping = new UrlMapping("aB3xY9z", LONG, NOW, null, false);
        when(cache.get("aB3xY9z")).thenReturn(Optional.empty());
        when(repository.findByShortCode("aB3xY9z")).thenReturn(Optional.of(mapping));

        assertThat(service.resolve("aB3xY9z")).isEqualTo(LONG);
        verify(cache).put(mapping, NOW);
    }

    @Test
    void resolve_unknownCode_is404() {
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(repository.findByShortCode("nope123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("nope123"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void resolve_expiredButNotYetDeletedByTtl_is410AndNotCached() {
        UrlMapping expired = new UrlMapping("old1234", LONG, NOW.minusSeconds(100), NOW.minusSeconds(1), false);
        when(cache.get("old1234")).thenReturn(Optional.empty());
        when(repository.findByShortCode("old1234")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resolve("old1234"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.GONE));
        verify(cache, never()).put(any(), any());
    }

    // ---- get (metadata) ----

    @Test
    void get_reportsExpiredLinksInsteadOfFailing() {
        when(repository.findByShortCode("old1234"))
                .thenReturn(Optional.of(new UrlMapping("old1234", LONG, NOW.minusSeconds(100), NOW.minusSeconds(1), false)));

        assertThat(service.get("old1234").expired()).isTrue();
    }
}
