package com.shortener.url_shortener.domain.url.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.shortener.url_shortener.container.IntegrationTestBase;
import com.shortener.url_shortener.domain.url.entity.ShortUrl;
import com.shortener.url_shortener.domain.url.repository.ShortUrlJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * URLShortenerController 통합 테스트
 *
 * 테스트 내용:
 * - 실제 HTTP 요청 + DB 연동
 * - 리다이렉션 전체 흐름 검증
 * - 만료된 링크 처리
 */
@DisplayName("URLShortenerController 통합 테스트")
class ShortUrlControllerIntegrationTest extends IntegrationTestBase {

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private ShortUrlJpaRepository shortUrlJpaRepository;

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
	}

	@Nested
	@DisplayName("GET /{key} 통합 테스트")
	class GetLinkIntegrationTest {

		@Test
		@DisplayName("성공: DB에서 조회 후 리다이렉션")
		void getLink_fullFlow_success() throws Exception {
			// given
			String hashKey = "testKey1";
			String redirectUrl = "https://example.com/test";
			ShortUrl entity = newShortUrl(123456789L, hashKey, redirectUrl, LocalDateTime.now().plusDays(7));
			shortUrlJpaRepository.save(entity);

			// when & then
			mockMvc.perform(get("/link/{key}", hashKey))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", redirectUrl));
		}

		@Test
		@DisplayName("실패: DB에 존재하지 않는 키 조회 시 404")
		void getLink_keyNotInDb_notFound() throws Exception {
			// given
			String nonExistentKey = "notExist";

			// when & then
			mockMvc.perform(get("/link/{key}", nonExistentKey))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").exists());
		}

		@Test
		@DisplayName("실패: 만료된 링크 조회 시 404")
		void getLink_expiredLink_notFound() throws Exception {
			// given
			String hashKey = "expired1";
			String redirectUrl = "https://example.com/expired";
			ShortUrl entity = newShortUrl(123456789L, hashKey, redirectUrl,
				LocalDateTime.now().minusDays(1) // 이미 만료됨
			);
			shortUrlJpaRepository.save(entity);

			// when & then
			mockMvc.perform(get("/link/{key}", hashKey))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("링크가 만료되었습니다."));
		}

		@Test
		@DisplayName("성공: 만료 직전 링크는 정상 리다이렉션")
		void getLink_almostExpired_success() throws Exception {
			// given
			String hashKey = "almostEx";
			String redirectUrl = "https://example.com/almost-expired";
			ShortUrl entity = newShortUrl(123456789L, hashKey, redirectUrl,
				LocalDateTime.now().plusMinutes(1) // 1분 남음
			);
			shortUrlJpaRepository.save(entity);

			// when & then
			mockMvc.perform(get("/link/{key}", hashKey))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", redirectUrl));
		}

		@Test
		@DisplayName("실패: 잘못된 키 형식 시 400")
		void getLink_invalidKeyFormat_badRequest() throws Exception {
			// given
			String invalidKey = "invalid@key!";

			// when & then
			mockMvc.perform(get("/link/{key}", invalidKey))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").exists());
		}

		@Test
		@DisplayName("성공: Base62 문자만 포함된 다양한 길이의 키")
		void getLink_variousKeyLengths() throws Exception {
			// given
			String[] keys = {"a", "aB", "aB3", "aB3Xy9Km" };

			for (String key : keys) {
				String redirectUrl = "https://example.com/" + key;
				ShortUrl entity = newShortUrl(System.currentTimeMillis(), key, redirectUrl,
					LocalDateTime.now().plusDays(7));
				shortUrlJpaRepository.save(entity);

				// when & then
				mockMvc.perform(get("/link/{key}", key))
					.andExpect(status().isFound())
					.andExpect(header().string("Location", redirectUrl));
			}
		}
	}

	@Nested
	@DisplayName("POST /link 통합 테스트")
	class CreateLinkIntegrationTest {

		@Test
		@DisplayName("성공: 단축 URL 생성 후 응답 반환")
		void createLink_success() throws Exception {
			// given
			String redirectUrl = "https://example.com/create";
			String requestBody = """
				{"redirectUrl":"%s"}
				""".formatted(redirectUrl);

			// when
			String responseBody = mockMvc.perform(post("/link")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shortCode").isNotEmpty())
				.andExpect(jsonPath("$.url").isNotEmpty())
				.andReturn()
				.getResponse()
				.getContentAsString();

			// then
			ShortUrl saved = shortUrlJpaRepository.findAll().get(0);
			assertThat(saved.getShortCode()).hasSize(8);
			assertThat(responseBody).contains(saved.getShortCode());
			assertThat(saved.getRedirectionUrl()).isEqualTo(redirectUrl);
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
				.andExpect(jsonPath("$.message").exists());
		}

		@Test
		@DisplayName("실패: 잘못된 URL 스킴이면 400")
		void createLink_invalidScheme_badRequest() throws Exception {
			// given
			String requestBody = """
				{"redirectUrl":"ftp://example.com"}
				""";

			// when & then
			mockMvc.perform(post("/link")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("올바르지 않은 파라미터입니다."));
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
				.andExpect(jsonPath("$.message").value("올바르지 않은 파라미터입니다."));
		}
	}

	private ShortUrl newShortUrl(Long id, String shortCode, String redirectUrl, LocalDateTime expiredAt) {
		return new ShortUrl(id, sha256(redirectUrl), shortCode, redirectUrl, expiredAt);
	}

	private byte[] sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return digest.digest(input.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

}
