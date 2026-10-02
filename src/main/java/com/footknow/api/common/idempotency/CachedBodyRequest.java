package com.footknow.api.common.idempotency;

import com.footknow.api.common.error.ApiException;
import com.footknow.api.common.error.ErrorCode;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class CachedBodyRequest
        extends HttpServletRequestWrapper {

    private byte[] cachedBody;

    public CachedBodyRequest(HttpServletRequest request) {
        super(request);
    }

    public void prepare(int maxBytes) throws IOException {
        if (cachedBody != null) {
            return;
        }

        if (getContentLengthLong() > maxBytes) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
        }

        byte[] bytes = super.getInputStream().readNBytes(maxBytes + 1);

        if (bytes.length > maxBytes) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
        }

        cachedBody = bytes;
    }

    public byte[] body() {
        if (cachedBody == null) {
            throw new IllegalStateException(
                    "The request body has not been prepared"
            );
        }

        return cachedBody.clone();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (cachedBody == null) {
            return super.getInputStream();
        }

        ByteArrayInputStream input =
                new ByteArrayInputStream(cachedBody);

        return new ServletInputStream() {
            @Override
            public int read() {
                return input.read();
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
                return input.read(bytes, offset, length);
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
            public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException(
                        "Non-blocking request reading is not supported"
                );
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        Charset charset = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(getCharacterEncoding());

        return new BufferedReader(
                new InputStreamReader(getInputStream(), charset)
        );
    }
}