package com.shortener.url_shortener.domain.url.controller;

import com.shortener.url_shortener.domain.url.service.URLShortenerService;
import com.shortener.url_shortener.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * URLShortenerController 단위 테스트
 * 
 * 테스트 내용:
 * - GET /{key}: 리다이렉션 성공/실패 케이스
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("URLShortenerController 단위 테스트")
class URLShortenerControllerTest {

	@InjectMocks
	private URLShortenerController urlShortenerController;

	@Mock
	private URLShortenerService urlShortenerService;

	@Nested
	@DisplayName("GET /{key} - 리다이렉션 테스트")
	class GetLinkTest {

		@Test
		@DisplayName("성공: 유효한 키로 리다이렉션")
		void getLink_success() {
			// given
			String hashKey = "aB3Xy9Km";
			String redirectUrl = "https://example.com/test";

			when(urlShortenerService.getLink(hashKey)).thenReturn(redirectUrl);

			// when
			ResponseEntity<Void> response = urlShortenerController.getLink(hashKey);

			// then
			assertEquals(HttpStatus.FOUND, response.getStatusCode());
			assertEquals(redirectUrl, response.getHeaders().getLocation().toString());
		}

		@Test
		@DisplayName("실패: 존재하지 않는 키 조회 시 예외 발생")
		void getLink_keyNotFound() {
			// given
			String hashKey = "notExist";

			when(urlShortenerService.getLink(hashKey))
				.thenThrow(ErrorCode.KEY_NOT_FOUND.baseException("Key not found"));

			// when & then
			assertThrows(Exception.class, () -> urlShortenerController.getLink(hashKey));
		}

		@Test
		@DisplayName("실패: 만료된 링크 조회 시 예외 발생")
		void getLink_expiredLink() {
			// given
			String hashKey = "expired1";

			when(urlShortenerService.getLink(hashKey))
				.thenThrow(ErrorCode.EXPIRED_LINK.baseException("Link expired"));

			// when & then
			assertThrows(Exception.class, () -> urlShortenerController.getLink(hashKey));
		}

		@Test
		@DisplayName("실패: 잘못된 키 형식 시 예외 발생")
		void getLink_invalidKey() {
			// given
			String hashKey = "invalid@key!";

			when(urlShortenerService.getLink(hashKey))
				.thenThrow(ErrorCode.INVALID_KEY_ERROR.baseException("Invalid key format"));

			// when & then
			assertThrows(Exception.class, () -> urlShortenerController.getLink(hashKey));
		}

		@Test
		@DisplayName("성공: Base62 문자만 포함된 키 허용")
		void getLink_validBase62Key() {
			// given
			String hashKey = "aB3Xy9Km"; // 0-9, a-z, A-Z
			String redirectUrl = "https://example.com";

			when(urlShortenerService.getLink(hashKey)).thenReturn(redirectUrl);

			// when
			ResponseEntity<Void> response = urlShortenerController.getLink(hashKey);

			// then
			assertEquals(HttpStatus.FOUND, response.getStatusCode());
			assertEquals(redirectUrl, response.getHeaders().getLocation().toString());
		}

		@Test
		@DisplayName("성공: 다양한 URL 형식 리다이렉션")
		void getLink_variousUrlFormats() {
			// given
			String hashKey = "test1234";
			String[] urls = {
				"https://example.com",
				"https://example.com/path",
				"https://example.com/path?query=value",
				"https://example.com/path#anchor",
				"https://sub.example.com:8080/path?q=1#top"
			};

			for (String url : urls) {
				when(urlShortenerService.getLink(hashKey)).thenReturn(url);

				// when
				ResponseEntity<Void> response = urlShortenerController.getLink(hashKey);

				// then
				assertEquals(HttpStatus.FOUND, response.getStatusCode());
				assertEquals(url, response.getHeaders().getLocation().toString());
			}
		}

		@Test
		@DisplayName("성공: Location 헤더가 올바르게 설정됨")
		void getLink_locationHeaderSetCorrectly() {
			// given
			String hashKey = "testKey";
			String redirectUrl = "https://example.com";

			when(urlShortenerService.getLink(hashKey)).thenReturn(redirectUrl);

			// when
			ResponseEntity<Void> response = urlShortenerController.getLink(hashKey);

			// then
			HttpHeaders headers = response.getHeaders();
			assertNotNull(headers.getLocation());
			assertEquals(redirectUrl, headers.getLocation().toString());
		}
	}
}
