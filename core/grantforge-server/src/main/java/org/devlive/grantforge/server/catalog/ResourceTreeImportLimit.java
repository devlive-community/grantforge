// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Limits resource tree JSON before it reaches the JSON parser, including chunked requests. */
@Component
public final class ResourceTreeImportLimit extends OncePerRequestFilter
{
    private final int maxBytes;

    /**
     * Creates the request size limit.
     * @param maxBytes the maximum number of bytes
     * @throws IllegalArgumentException if the limit is out of range
     */
    public ResourceTreeImportLimit(@Value("${grantforge.resource-import.max-bytes:1048576}") int maxBytes)
    {
        if (maxBytes < 1 || maxBytes > 67108864) {
            throw new IllegalArgumentException("grantforge.resource-import.max-bytes must be between 1 and 67108864");
        }
        this.maxBytes = maxBytes;
    }

    /**
     * Returns the configured limit.
     * @return the maximum request size in bytes
     */
    public int maxBytes()
    {
        return maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!"POST".equals(request.getMethod()) || !path.matches("/api/v1/applications/[^/]+/resource-tree/import")) {
            chain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > maxBytes) {
            tooLarge(response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            tooLarge(response);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream()
            {
                ByteArrayInputStream input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read()
                    {
                        return input.read();
                    }
                    @Override public boolean isFinished()
                    {
                        return input.available() == 0;
                    }
                    @Override public boolean isReady()
                    {
                        return true;
                    }
                    @Override public void setReadListener(ReadListener listener)
                    {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        }, response);
    }

    private void tooLarge(HttpServletResponse response) throws IOException
    {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":413,\"title\":\"Resource tree import exceeds " + maxBytes + " bytes\"}");
    }
}
