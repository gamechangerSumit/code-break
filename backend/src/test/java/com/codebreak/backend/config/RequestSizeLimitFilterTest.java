package com.codebreak.backend.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class RequestSizeLimitFilterTest {

    @Test
    void rejectsBodyWhenContentLengthExceedsLimitWithoutCallingChain() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(8);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("123456789".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Content-Length", "9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("maximum allowed size"));
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsChunkedOrUnknownLengthBodyWhenReadLimitIsExceeded() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(8);
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContent("123456789".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passesSmallBodyDownstreamAndMakesItReadable() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(8);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("hello".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (wrappedRequest, wrappedResponse) -> {
            assertEquals("hello", new String(
                    ((jakarta.servlet.http.HttpServletRequest) wrappedRequest).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ));
        };

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsInvalidLimitConfiguration() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new RequestSizeLimitFilter(0)
        );
    }
}
