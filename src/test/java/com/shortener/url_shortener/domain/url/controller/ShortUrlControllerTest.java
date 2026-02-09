package com.shortener.url_shortener.domain.url.controller;

import com.shortener.url_shortener.domain.url.dto.response.ShortUrlCreateResponse;
import com.shortener.url_shortener.domain.url.service.ShortUrlService;
import com.shortener.url_shortener.global.error.ErrorCode;
import com.shortener.url_shortener.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ShortUrlController WebMvc 테스트
 */
@WebMvcTest(ShortUrlController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("ShortUrlController WebMvc 테스트")
class ShortUrlControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ShortUrlService shortUrlService;

	@Nested
	@DisplayName("GET /{key} - 리다이렉션 테스트")
	class GetLinkTest {

		@Test
		@DisplayName("성공: 유효한 키로 리다이렉션")
		void getLink_success() throws Exception {
			// given
			String shortCode = "aB3Xy9Km";
			String redirectUrl = "https://example.com/test";
			when(shortUrlService.getLink(shortCode)).thenReturn(redirectUrl);

			// when & then
			mockMvc.perform(get("/link/{key}", shortCode))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", redirectUrl));
		}

		@Test
		@DisplayName("실패: 존재하지 않는 키 조회 시 404")
		void getLink_keyNotFound() throws Exception {
			// given
			String shortCode = "notExist";
			when(shortUrlService.getLink(shortCode))
				.thenThrow(ErrorCode.KEY_NOT_FOUND.baseException("Key not found"));

			// when & then
			mockMvc.perform(get("/link/{key}", shortCode))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(ErrorCode.KEY_NOT_FOUND.getMessage()));
		}

		@Test
		@DisplayName("실패: 잘못된 키 형식 시 400")
		void getLink_invalidKey() throws Exception {
			// given
			String shortCode = "invalid@key!";
			when(shortUrlService.getLink(shortCode))
				.thenThrow(ErrorCode.INVALID_KEY_ERROR.baseException("Invalid key format"));

			// when & then
			mockMvc.perform(get("/link/{key}", shortCode))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_KEY_ERROR.getMessage()));
		}
	}

	@Nested
	@DisplayName("POST /link - 생성 테스트")
	class CreateLinkTest {

		@Test
		@DisplayName("성공: 단축 URL 생성")
		void createLink_success() throws Exception {
			// given
			String redirectUrl = "https://example.com";
			String shortCode = "aB3Xy9Km";
			String shortUrl = "http://localhost:8080/" + shortCode;
			String requestBody = """
				{"redirectUrl":"%s"}
				""".formatted(redirectUrl);

			when(shortUrlService.createLink(redirectUrl))
				.thenReturn(new ShortUrlCreateResponse(shortCode, shortUrl));

			// when & then
			mockMvc.perform(post("/link")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shortCode").value(shortCode))
				.andExpect(jsonPath("$.url").value(shortUrl));
		}

		@Test
		@DisplayName("실패: redirectUrl 누락 시 400")
		void createLink_missingRedirectUrl_badRequest() throws Exception {
			// given
			String requestBody = """
				{"redirectUrl":""}
				""";

			// when & then
			mockMvc.perform(post("/link")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_ARGUMENT_ERROR.getMessage()));
		}

		@Test
		@DisplayName("실패: 잘못된 URL 스킴이면 400")
		void createLink_invalidScheme_badRequest() throws Exception {
			// given
			String requestBody = """
				{"redirectUrl":"ftp://example.com"}
				""";
			when(shortUrlService.createLink("ftp://example.com"))
				.thenThrow(ErrorCode.INVALID_ARGUMENT_ERROR.baseException("Invalid scheme"));

			// when & then
			mockMvc.perform(post("/link")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_ARGUMENT_ERROR.getMessage()));
		}

		@Test
		@DisplayName("실패: URL 길이가 제한을 초과하면 400")
		void createLink_exceedsMaxLength_badRequest() throws Exception {
			// given
			String longPath = "a".repeat(2100);
			String redirectUrl = "http://example.com/" + longPath;
			String requestBody = """
				{"redirectUrl":"%s"}
				""".formatted(redirectUrl);

			// when & then
			mockMvc.perform(post("/link")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_ARGUMENT_ERROR.getMessage()));
		}
	}
}
