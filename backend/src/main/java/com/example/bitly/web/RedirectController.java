package com.example.bitly.web;

import com.example.bitly.url.UrlService;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read service: the hot path.
 *
 * <p>302 (not 301): browsers cache 301s forever, so later expiration or deletion would never be
 * seen and every click after the first would bypass us.
 */
@RestController
public class RedirectController {

    private final UrlService urls;

    public RedirectController(UrlService urls) {
        this.urls = urls;
    }

    // The regex keeps paths like /favicon.ico (contain '.') out of the redirect route.
    @GetMapping("/{shortCode:[A-Za-z0-9_-]+}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(urls.resolve(shortCode)))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
