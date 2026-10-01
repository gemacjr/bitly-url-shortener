package com.example.bitly.web;

import com.example.bitly.url.UrlDtos.ShortenRequest;
import com.example.bitly.url.UrlDtos.UrlResponse;
import com.example.bitly.url.UrlService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Write service: create short URLs and look up their metadata. */
@RestController
@RequestMapping("/urls")
public class UrlController {

    private final UrlService urls;

    public UrlController(UrlService urls) {
        this.urls = urls;
    }

    @PostMapping
    public ResponseEntity<UrlResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        UrlResponse created = urls.shorten(request);
        return ResponseEntity.created(URI.create("/urls/" + created.shortCode())).body(created);
    }

    @GetMapping("/{shortCode}")
    public UrlResponse get(@PathVariable String shortCode) {
        return urls.get(shortCode);
    }
}
