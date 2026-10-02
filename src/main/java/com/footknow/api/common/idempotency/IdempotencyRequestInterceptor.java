package com.footknow.api.common.idempotency;

import com.footknow.api.common.security.CurrentCaller;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

@Component
public class IdempotencyRequestInterceptor implements HandlerInterceptor {

	private final CurrentCaller currentCaller;
	private final IdempotencyKeyValidator keyValidator;
	private final RequestFingerprint fingerprint;
	private final IdempotencyProperties properties;

	public IdempotencyRequestInterceptor(CurrentCaller currentCaller, IdempotencyKeyValidator keyValidator,
			RequestFingerprint fingerprint, IdempotencyProperties properties) {
		this.currentCaller = currentCaller;
		this.keyValidator = keyValidator;
		this.fingerprint = fingerprint;
		this.properties = properties;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		if (request.getDispatcherType() != DispatcherType.REQUEST || !(handler instanceof HandlerMethod method)) {
			return true;
		}

		Idempotent annotation = method.getMethodAnnotation(Idempotent.class);

		if (annotation == null) {
			return true;
		}

		if (!"POST".equals(request.getMethod())
				|| !ResponseEntity.class.isAssignableFrom(method.getMethod().getReturnType())
				|| !annotation.operation().matches("[a-z][a-z0-9.-]{1,127}")) {
			throw new IllegalStateException("Unsupported idempotent endpoint declaration");
		}

		String key = keyValidator.requireKey(request);

		CachedBodyRequest wrapped = WebUtils.getNativeRequest(request, CachedBodyRequest.class);

		if (wrapped == null) {
			throw new IllegalStateException("Idempotency request wrapper is missing");
		}

		wrapped.prepare(properties.maxRequestBytes());

		IdempotencyScope scope = new IdempotencyScope(currentCaller.userId(), annotation.operation(), key);

		String requestFingerprint = fingerprint.calculate(request.getMethod(), request.getRequestURI(),
				request.getQueryString(), wrapped.body());

		request.setAttribute(IdempotencyRequestContext.ATTRIBUTE,
				new IdempotencyRequestContext(scope, requestFingerprint));

		return true;
	}
}