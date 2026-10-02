package com.footknow.api.common.handler;

import com.footknow.api.common.response.ApiResponse;

import com.footknow.api.common.error.ApiError;
import com.footknow.api.common.error.ApiException;
import com.footknow.api.common.error.ErrorCode;
import com.footknow.api.common.error.FieldViolation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Global exception handler for the API.
 * <p>
 * This class handles various exceptions that may occur during the processing of
 * API requests and provides standardized error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Object> handleApiException(ApiException exception) {
		ErrorCode errorCode = exception.errorCode();

		if (errorCode.status().is5xxServerError()) {
			log.error("Application server error", exception);
		}

		return errorResponse(errorCode, errorCode.status(), new HttpHeaders(), List.of());
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Stream<FieldViolation> fieldViolations = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldViolation(error.getField(), validationMessage(error.getDefaultMessage())));

		Stream<FieldViolation> globalViolations = exception.getBindingResult().getGlobalErrors().stream()
				.map(error -> new FieldViolation("$", validationMessage(error.getDefaultMessage())));

		List<FieldViolation> violations = Stream.concat(fieldViolations, globalViolations).distinct()
				.sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message)).toList();

		return errorResponse(ErrorCode.VALIDATION_FAILED, status, headers, violations);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return errorResponse(ErrorCode.MALFORMED_REQUEST, status, headers, List.of());
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		if (status.is5xxServerError()) {
			log.error("Spring MVC server error", exception);
		}

		return errorResponse(errorCodeFor(status), status, headers, List.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleUnexpectedException(Exception exception) {
		log.error("Unhandled request error", exception);

		return errorResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.status(), new HttpHeaders(), List.of());
	}

	private ResponseEntity<Object> errorResponse(ErrorCode errorCode, HttpStatusCode status, HttpHeaders headers,
			List<FieldViolation> violations) {
		ApiError error = new ApiError(errorCode.name(), errorCode.message(), violations);

		return new ResponseEntity<>(ApiResponse.failure(error), headers, status);
	}

	private ErrorCode errorCodeFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> ErrorCode.BAD_REQUEST;
			case 401 -> ErrorCode.UNAUTHORIZED;
			case 403 -> ErrorCode.FORBIDDEN;
			case 404 -> ErrorCode.NOT_FOUND;
			case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
			case 409 -> ErrorCode.CONFLICT;
			case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
			case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
			case 429 -> ErrorCode.RATE_LIMIT_EXCEEDED;
			default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.REQUEST_REJECTED;
		};
	}

	private static String validationMessage(String message) {
		return message == null ? "Invalid value." : message;
	}
}