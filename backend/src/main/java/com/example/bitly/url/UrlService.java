package com.example.bitly.url;

import com.example.bitly.common.ApiException;
import com.example.bitly.config.AppProperties;
import com.example.bitly.shortcode.ShortCodeGenerator;
import com.example.bitly.url.UrlDtos.ShortenRequest;
import com.example.bitly.url.UrlDtos.UrlResponse;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    /**
     * Counter codes are unique among themselves, but one can still land on a custom alias someone
     * already claimed (same namespace). The conditional write catches that and we take the next code.
     */
    static final int MAX_GENERATION_ATTEMPTS = 5;

    private final UrlRepository repository;
    private final UrlCache cache;
    private final ShortCodeGenerator generator;
    private final AppProperties props;
    private final URI shortDomain;
    private final Clock clock;

    public UrlService(UrlRepository repository, UrlCache cache, ShortCodeGenerator generator,
                      AppProperties props, Clock clock) {
        this.repository = repository;
        this.cache = cache;
        this.generator = generator;
        this.props = props;
        this.shortDomain = URI.create(props.baseUrl());
        this.clock = clock;
    }

    /** Write path: POST /urls. */
    public UrlResponse shorten(ShortenRequest request) {
        // DynamoDB stores epoch seconds; truncate so POST and later GETs report identical instants.
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        String longUrl = UrlRules.validateLongUrl(request.longUrl(), shortDomain);
        Instant expiresAt = request.expirationDate() == null ? null : request.expirationDate().truncatedTo(ChronoUnit.SECONDS);
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw ApiException.badRequest("expiration_date must be in the future");
        }

        String alias = request.customAlias() == null ? "" : request.customAlias().trim();
        UrlMapping saved = alias.isEmpty()
                ? saveWithGeneratedCode(longUrl, now, expiresAt)
                : saveWithAlias(alias, longUrl, now, expiresAt);
        return toResponse(saved, now);
    }

    /** Read path: GET /{shortCode}. Cache first, DynamoDB on miss. */
    public String resolve(String shortCode) {
        Optional<String> cached = cache.get(shortCode);
        if (cached.isPresent()) {
            return cached.get();
        }
        Instant now = clock.instant();
        UrlMapping mapping = findOrThrow(shortCode);
        if (mapping.isExpired(now)) {
            throw ApiException.gone("Short URL '" + shortCode + "' has expired");
        }
        cache.put(mapping, now);
        return mapping.longUrl();
    }

    /** GET /urls/{shortCode}: metadata, including expired links (no 410 here). */
    public UrlResponse get(String shortCode) {
        return toResponse(findOrThrow(shortCode), clock.instant());
    }

    private UrlMapping saveWithAlias(String alias, String longUrl, Instant now, Instant expiresAt) {
        UrlRules.validateAlias(alias);
        UrlMapping mapping = new UrlMapping(alias, longUrl, now, expiresAt, true);
        if (!repository.saveIfAbsent(mapping)) {
            throw ApiException.conflict("custom_alias '" + alias + "' is already taken");
        }
        return mapping;
    }

    private UrlMapping saveWithGeneratedCode(String longUrl, Instant now, Instant expiresAt) {
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            UrlMapping mapping = new UrlMapping(generator.nextCode(), longUrl, now, expiresAt, false);
            if (repository.saveIfAbsent(mapping)) {
                return mapping;
            }
            log.info("Generated code {} was already taken (custom alias?), retrying", mapping.shortCode());
        }
        throw new IllegalStateException("Could not allocate a unique short code after "
                + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    private UrlMapping findOrThrow(String shortCode) {
        return repository.findByShortCode(shortCode)
                .orElseThrow(() -> ApiException.notFound("Short URL '" + shortCode + "' not found"));
    }

    private UrlResponse toResponse(UrlMapping m, Instant now) {
        return new UrlResponse(props.shortUrl(m.shortCode()), m.shortCode(), m.longUrl(), m.customAlias(),
                m.createdAt(), m.expiresAt(), m.isExpired(now));
    }
}
