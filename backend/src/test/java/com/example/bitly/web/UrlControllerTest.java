package com.example.bitly.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bitly.common.ApiException;
import com.example.bitly.url.UrlDtos.ShortenRequest;
import com.example.bitly.url.UrlDtos.UrlResponse;
import com.example.bitly.url.UrlService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP contract of the write API: snake_case JSON, status codes and ProblemDetail errors. */
@WebMvcTest(UrlController.class)
class UrlControllerTest {

    private static final Instant CREATED = Instant.parse("2030-01-01T12:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean UrlService urlService;

    @Test
    void create_returns201WithSnakeCaseBodyAndLocation() throws Exception {
        when(urlService.shorten(new ShortenRequest("https://example.com", "promo", Instant.parse("2031-01-01T00:00:00Z"))))
                .thenReturn(new UrlResponse("http://sho.rt/promo", "promo", "https://example.com", true,
                        CREATED, Instant.parse("2031-01-01T00:00:00Z"), false));

        mvc.perform(post("/urls").contentType(MediaType.APPLICATION_JSON).content("""
                        {"long_url":"https://example.com","custom_alias":"promo","expiration_date":"2031-01-01T00:00:00Z"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/urls/promo"))
                .andExpect(jsonPath("$.short_url").value("http://sho.rt/promo"))
                .andExpect(jsonPath("$.short_code").value("promo"))
                .andExpect(jsonPath("$.custom_alias").value(true))
                .andExpect(jsonPath("$.expiration_date").value("2031-01-01T00:00:00Z"));
    }

    @Test
    void create_missingLongUrl_is400() throws Exception {
        mvc.perform(post("/urls").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("longUrl: must not be blank"));
        verifyNoInteractions(urlService);
    }

    @Test
    void create_badDate_is400() throws Exception {
        mvc.perform(post("/urls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"long_url\":\"https://example.com\",\"expiration_date\":\"next tuesday\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_aliasTaken_is409ProblemDetail() throws Exception {
        when(urlService.shorten(any())).thenThrow(ApiException.conflict("custom_alias 'promo' is already taken"));

        mvc.perform(post("/urls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"long_url\":\"https://example.com\",\"custom_alias\":\"promo\"}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.detail").value("custom_alias 'promo' is already taken"));
    }

    @Test
    void getMetadata_returnsMapping() throws Exception {
        when(urlService.get("aB3xY9z")).thenReturn(new UrlResponse("http://sho.rt/aB3xY9z", "aB3xY9z",
                "https://example.com", false, CREATED, null, false));

        mvc.perform(get("/urls/aB3xY9z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.long_url").value("https://example.com"))
                .andExpect(jsonPath("$.expired").value(false));
    }

    @Test
    void getMetadata_unknown_is404() throws Exception {
        when(urlService.get("nope")).thenThrow(ApiException.notFound("Short URL 'nope' not found"));

        mvc.perform(get("/urls/nope")).andExpect(status().isNotFound());
    }
}
