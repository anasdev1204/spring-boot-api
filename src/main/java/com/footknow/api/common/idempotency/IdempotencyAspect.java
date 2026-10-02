package com.footknow.api.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footknow.api.common.error.ApiException;
import com.footknow.api.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.UUID;

@Aspect
@Component
public class IdempotencyAspect {

	public static final String REPLAY_HEADER = "Idempotency-Replayed";

	private final IdempotencyStore store;
	private final ObjectMapper objectMapper;
	private final IdempotencyProperties properties;

	public IdempotencyAspect(IdempotencyStore store, ObjectMapper objectMapper, IdempotencyProperties properties) {
		this.store = store;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	@Around(value = "@annotation(annotation)", argNames = "joinPoint,annotation")
	public Object execute(ProceedingJoinPoint joinPoint, Idempotent annotation) throws Throwable {
		IdempotencyRequestContext context = requestContext();

		if (!annotation.operation().equals(context.scope().operation())) {
			throw new IllegalStateException("Idempotency operation does not match request context");
		}

		IdempotencyAcquisition acquisition = store.acquire(context.scope(), context.fingerprint());

		if (acquisition instanceof IdempotencyAcquisition.Replay replay) {
			return response(replay.response(), true);
		}

		if (acquisition instanceof IdempotencyAcquisition.Rejected rejected) {
			throw rejection(rejected);
		}

		UUID ownerToken = ((IdempotencyAcquisition.Acquired) acquisition).ownerToken();

		try {
			Object result = joinPoint.proceed();

			if (!(result instanceof ResponseEntity<?> original)) {
				throw new IllegalStateException("Idempotent endpoints must return ResponseEntity");
			}

			StoredHttpResponse snapshot = snapshot(original);

			// Build the outgoing response before completing the reservation.
			ResponseEntity<byte[]> outgoing = response(snapshot, false);

			IdempotencyStore.CompletionResult completion = store.complete(context.scope(), ownerToken, snapshot);

			if (completion != IdempotencyStore.CompletionResult.COMPLETED) {
				throw new ApiException(ErrorCode.INTERNAL_ERROR);
			}

			return outgoing;
		} catch (Throwable failure) {
			try {
				store.markOutcomeUnknown(context.scope(), ownerToken);
			} catch (RuntimeException markingFailure) {
				failure.addSuppressed(markingFailure);
			}

			throw failure;
		}
	}

	private IdempotencyRequestContext requestContext() {
		ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
				.currentRequestAttributes();

		HttpServletRequest request = attributes.getRequest();

		Object value = request.getAttribute(IdempotencyRequestContext.ATTRIBUTE);

		if (!(value instanceof IdempotencyRequestContext context)) {
			throw new IllegalStateException("Idempotency request context is missing");
		}

		return context;
	}

	private StoredHttpResponse snapshot(ResponseEntity<?> original) {
		MediaType contentType = original.getHeaders().getContentType();

		if (contentType == null) {
			contentType = MediaType.APPLICATION_JSON;
		}

		if (!MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
			throw new IllegalStateException("Only JSON responses are supported");
		}

		byte[] body = serialize(original.getBody());

		return new StoredHttpResponse(original.getStatusCode().value(), contentType.toString(),
				original.getHeaders().getFirst(HttpHeaders.LOCATION), body);
	}

	private byte[] serialize(Object body) {
		if (body == null) {
			return new byte[0];
		}

		LimitedOutputStream output = new LimitedOutputStream(properties.maxResponseBytes());

		try {
			objectMapper.writeValue(output, body);
			return output.toByteArray();
		} catch (IOException exception) {
			throw new ApiException(ErrorCode.INTERNAL_ERROR, exception);
		}
	}

	private ResponseEntity<byte[]> response(StoredHttpResponse snapshot, boolean replayed) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(snapshot.status())
				.cacheControl(CacheControl.noStore()).header(REPLAY_HEADER, Boolean.toString(replayed));

		if (snapshot.contentType() != null) {
			builder.header(HttpHeaders.CONTENT_TYPE, snapshot.contentType());
		}

		if (snapshot.location() != null) {
			builder.header(HttpHeaders.LOCATION, snapshot.location());
		}

		return builder.body(snapshot.body());
	}

	private ApiException rejection(IdempotencyAcquisition.Rejected rejected) {
		ErrorCode errorCode = switch (rejected) {
			case REQUEST_MISMATCH -> ErrorCode.IDEMPOTENCY_KEY_REUSED;
			case IN_PROGRESS -> ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS;
			case OUTCOME_UNKNOWN -> ErrorCode.IDEMPOTENCY_OUTCOME_UNKNOWN;
			case CAPACITY_EXHAUSTED -> ErrorCode.SERVICE_UNAVAILABLE;
		};

		return new ApiException(errorCode);
	}

	private static final class LimitedOutputStream extends OutputStream {

		private final int limit;
		private final ByteArrayOutputStream delegate;

		private LimitedOutputStream(int limit) {
			this.limit = limit;
			this.delegate = new ByteArrayOutputStream(Math.min(limit, 1024));
		}

		@Override
		public void write(int value) throws IOException {
			ensureCapacity(1);
			delegate.write(value);
		}

		@Override
		public void write(byte[] bytes, int offset, int length) throws IOException {
			Objects.checkFromIndexSize(offset, length, bytes.length);
			ensureCapacity(length);
			delegate.write(bytes, offset, length);
		}

		private void ensureCapacity(int additionalBytes) throws IOException {
			if (additionalBytes > limit - delegate.size()) {
				throw new IOException("Idempotent response exceeds the retention limit");
			}
		}

		private byte[] toByteArray() {
			return delegate.toByteArray();
		}
	}
}