package com.footknow.api.common.idempotency;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

@Component
public class RequestFingerprint {

    private static final String FORMAT_VERSION =
            "footknow-idempotency-fingerprint-v1";

    public String calculate(
            String method,
            String requestUri,
            String queryString,
            byte[] body
    ) {
        Objects.requireNonNull(method, "method is required");
        Objects.requireNonNull(requestUri, "requestUri is required");
        Objects.requireNonNull(body, "body is required");

        MessageDigest digest = sha256();

        updateText(digest, FORMAT_VERSION);
        updateText(digest, method.toUpperCase(Locale.ROOT));
        updateText(digest, requestUri);
        updateText(digest, queryString);
        updateBytes(digest, body);

        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private void updateText(
            MessageDigest digest,
            String value
    ) {
        if (value == null) {
            updateLength(digest, -1);
            return;
        }

        updateBytes(
                digest,
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void updateBytes(
            MessageDigest digest,
            byte[] value
    ) {
        updateLength(digest, value.length);
        digest.update(value);
    }

    private void updateLength(
            MessageDigest digest,
            int length
    ) {
        digest.update(
                ByteBuffer.allocate(Integer.BYTES)
                        .putInt(length)
                        .array()
        );
    }
}