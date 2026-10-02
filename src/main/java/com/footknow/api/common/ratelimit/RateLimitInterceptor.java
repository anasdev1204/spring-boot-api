package com.footknow.api.common.ratelimit;

import com.footknow.api.common.security.CurrentCaller;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	private final CurrentCaller currentCaller;
	private final RateLimitTierResolver tierResolver;
	private final RateLimitProperties properties;
	private final RateLimitStore store;

	public RateLimitInterceptor(CurrentCaller currentCaller, RateLimitTierResolver tierResolver,
			RateLimitProperties properties, RateLimitStore store) {
		this.currentCaller = currentCaller;
		this.tierResolver = tierResolver;
		this.properties = properties;
		this.store = store;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// Do not charge again for async or error redispatch.
		if (request.getDispatcherType() != DispatcherType.REQUEST) {
			return true;
		}

		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}

		RateLimited annotation = handlerMethod.getMethodAnnotation(RateLimited.class);

		if (annotation == null) {
			annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RateLimited.class);
		}

		if (annotation == null) {
			return true;
		}

		String callerId = currentCaller.userId();

		RateLimitTier tier = tierResolver.resolve(callerId);
		RateLimitCategory category = annotation.category();

		RateLimitDecision decision = store.tryAcquire(callerId, category, properties.policyFor(tier, category));

		if (!decision.allowed()) {
			throw new RateLimitExceededException(decision.retryAfterSeconds());
		}

		return true;
	}
}