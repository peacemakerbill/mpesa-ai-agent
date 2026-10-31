package com.mganji.ganji.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private MockHttpServletResponse preflight(String configured, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/internal/actions");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "x-internal-key,content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CorsConfig.corsFilter(configured).doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void nothingIsAllowedWhenNoOriginIsConfigured() throws Exception {
        assertThat(preflight("", "http://localhost:8000").getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(preflight(null, "http://localhost:8000").getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void aConfiguredOriginMaySendTheInternalKeyHeader() throws Exception {
        MockHttpServletResponse response = preflight("http://localhost:8000, http://127.0.0.1:8000", "http://localhost:8000");

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:8000");
        assertThat(response.getHeader("Access-Control-Allow-Headers")).containsIgnoringCase("x-internal-key");
    }

    @Test
    void otherOriginsAndWildcardsAreRefused() throws Exception {
        assertThat(preflight("http://localhost:8000", "http://evil.example").getStatus()).isEqualTo(403);
        assertThat(preflight("*", "http://evil.example").getHeader("Access-Control-Allow-Origin")).isNull();
    }
}
