package com.footknow.api.common.error;

import com.footknow.api.common.response.ApiResponse;
import com.footknow.api.common.security.CurrentCaller;
import com.footknow.api.common.handler.GlobalExceptionHandler;
import com.footknow.api.common.ratelimit.RateLimitInterceptor;
import com.footknow.api.common.ratelimit.RateLimitProperties;
import com.footknow.api.common.ratelimit.RateLimitTierResolver;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CurrentCaller currentCaller;

	@MockitoBean
	private RateLimitTierResolver rateLimitTierResolver;

	@MockitoBean
	private RateLimitInterceptor rateLimitInterceptor;

	@MockitoBean
	private RateLimitProperties rateLimitProperties;

	private MockHttpServletRequestBuilder authenticatedPost(String path) {
		return post(path)
				.with(jwt().jwt(token -> token.subject("user_endpoint_test").claim("azp", "http://localhost:3000")));
	}

	private MockHttpServletRequestBuilder authenticatedGet(String path) {
		return get(path)
				.with(jwt().jwt(token -> token.subject("user_endpoint_test").claim("azp", "http://localhost:3000")));
	}

	@Test
	void returnsSuccessEnvelope() throws Exception {
		mockMvc.perform(authenticatedPost("/_test/validation").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"Example"}
				""")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.name").value("Example")).andExpect(jsonPath("$.error").doesNotExist())
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void returnsFieldValidationErrors() throws Exception {
		mockMvc.perform(authenticatedPost("/_test/validation").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":""}
				""")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.violations[0].field").value("name"))
				.andExpect(jsonPath("$.error.violations[0].message").value("must not be blank"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void returnsMalformedRequestForInvalidJson() throws Exception {
		mockMvc.perform(authenticatedPost("/_test/validation").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
	}

	@Test
	void rejectsUnknownJsonProperties() throws Exception {
		mockMvc.perform(authenticatedPost("/_test/validation").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "name":"Example",
				  "unexpected":true
				}
				""")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
	}

	@Test
	void returnsPresetNotFoundError() throws Exception {
		mockMvc.perform(authenticatedGet("/_test/missing")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.error.message").value("The requested resource was not found."));
	}

	@Test
	void preservesMethodNotAllowedHeader() throws Exception {
		mockMvc.perform(authenticatedGet("/_test/validation")).andExpect(status().isMethodNotAllowed())
				.andExpect(header().string("Allow", containsString("POST")))
				.andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void hidesUnexpectedExceptionDetails() throws Exception {
		mockMvc.perform(authenticatedGet("/_test/unexpected")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error.message").value("An unexpected error occurred."))
				.andExpect(content().string(not(containsString("private-database-details"))));
	}

	@RestController
	@RequestMapping("/_test")
	public static class TestController {

		@PostMapping("/validation")
		public ApiResponse<TestRequest> validate(@Valid @RequestBody TestRequest request) {
			return ApiResponse.success(request);
		}

		@GetMapping("/missing")
		public void missing() {
			throw new ApiException(ErrorCode.NOT_FOUND);
		}

		@GetMapping("/unexpected")
		public void unexpected() {
			throw new IllegalStateException("private-database-details");
		}
	}

	public record TestRequest(@NotBlank(message = "must not be blank") String name) {
	}
}