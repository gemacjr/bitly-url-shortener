package com.example.bitly.url;

import com.example.bitly.common.ApiException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Input validation for long URLs and custom aliases. */
final class UrlRules {

    static final int MAX_URL_LENGTH = 2048;

    private static final Pattern ALIAS = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");

    /**
     * Short codes live at the root ({@code GET /{code}}), so an alias must not shadow our own
     * routes or anything we may add later.
     */
    private static final Set<String> RESERVED_ALIASES =
            Set.of("urls", "api", "actuator", "health", "admin", "login", "logout", "static", "assets");

    private UrlRules() {}

    /**
     * @param shortDomain base URL of our own short links; shortening those would create redirect loops
     * @return the trimmed URL
     */
    static String validateLongUrl(String rawUrl, URI shortDomain) {
        String url = rawUrl.trim();
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw ApiException.badRequest("long_url is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw ApiException.badRequest("long_url must start with http:// or https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw ApiException.badRequest("long_url must include a host");
        }
        if (uri.getHost().equalsIgnoreCase(shortDomain.getHost()) && effectivePort(uri) == effectivePort(shortDomain)) {
            throw ApiException.badRequest("long_url must not point at this URL shortener");
        }
        return url;
    }

    static void validateAlias(String alias) {
        if (!ALIAS.matcher(alias).matches()) {
            throw ApiException.badRequest("custom_alias must be 3-32 characters of letters, digits, '-' or '_'");
        }
        if (RESERVED_ALIASES.contains(alias.toLowerCase(Locale.ROOT))) {
            throw ApiException.badRequest("custom_alias '" + alias + "' is reserved");
        }
    }

    // Comparing ports too keeps e.g. localhost:3001 shortenable when the short domain is localhost:8090.
    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
