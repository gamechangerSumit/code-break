package com.codebreak.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final int maxRequestSizeBytes;

    public RequestSizeLimitFilter(
            @Value("${app.http.max-request-size-bytes:67108864}") long maxRequestSizeBytes
    ) {
        if (maxRequestSizeBytes < 1 || maxRequestSizeBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "app.http.max-request-size-bytes must be between 1 and 2147483646"
            );
        }
        this.maxRequestSizeBytes = (int) maxRequestSizeBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxRequestSizeBytes) {
            writePayloadTooLarge(response);
            return;
        }

        byte[] body;
        try (InputStream input = request.getInputStream()) {
            body = input.readNBytes(maxRequestSizeBytes + 1);
        }

        if (body.length > maxRequestSizeBytes) {
            writePayloadTooLarge(response);
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private static void writePayloadTooLarge(HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"Request body exceeds the maximum allowed size\"}");
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    if (readListener == null) {
                        throw new IllegalArgumentException("ReadListener must not be null");
                    }
                    try {
                        if (isFinished()) {
                            readListener.onAllDataRead();
                        } else {
                            readListener.onDataAvailable();
                        }
                    } catch (IOException exception) {
                        readListener.onError(exception);
                    }
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), getCharacterEncoding() == null
                            ? StandardCharsets.UTF_8
                            : java.nio.charset.Charset.forName(getCharacterEncoding()))
            );
        }
    }
}
