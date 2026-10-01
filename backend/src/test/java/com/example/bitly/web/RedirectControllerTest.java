package com.example.bitly.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bitly.common.ApiException;
import com.example.bitly.url.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean UrlService urlService;

    @Test
    void redirectsWith302AndNoStore() throws Exception {
        when(urlService.resolve("aB3xY9z")).thenReturn("https://www.example.com/long?q=1");

        mvc.perform(get("/aB3xY9z"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.example.com/long?q=1"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void expired_is410() throws Exception {
        when(urlService.resolve("old1234")).thenThrow(ApiException.gone("Short URL 'old1234' has expired"));

        mvc.perform(get("/old1234"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.detail").value("Short URL 'old1234' has expired"));
    }

    @Test
    void unknown_is404() throws Exception {
        when(urlService.resolve("nope123")).thenThrow(ApiException.notFound("Short URL 'nope123' not found"));

        mvc.perform(get("/nope123")).andExpect(status().isNotFound());
    }

    @Test
    void pathsWithDotsAreNotTreatedAsShortCodes() throws Exception {
        mvc.perform(get("/favicon.ico")).andExpect(status().isNotFound());
        verifyNoInteractions(urlService);
    }
}
