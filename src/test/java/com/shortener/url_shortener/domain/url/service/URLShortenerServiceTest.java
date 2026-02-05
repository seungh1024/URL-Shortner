package com.shortener.url_shortener.domain.url.service;

import com.shortener.url_shortener.domain.url.dto.response.LinkCreateResponse;
import com.shortener.url_shortener.domain.url.entity.URLShortener;
import com.shortener.url_shortener.domain.url.repository.URLShortenerJpaRepository;
import com.shortener.url_shortener.global.error.CustomException;
import com.shortener.url_shortener.global.error.ErrorCode;
import com.shortener.url_shortener.global.util.Base62Encoder;
import com.shortener.url_shortener.global.util.HashGenerator;
import com.shortener.url_shortener.global.util.TsidGenerator;
import io.grpc.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * URLShortenerService 단위 테스트
 * 
 * 테스트 내용:
 * - createLink: 성공, 충돌 재시도, 실패
 * - getLink: 성공, 키 없음, 만료
 * - deleteLink: 성공, 잘못된 키
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("URLShortenerService 단위 테스트")
class URLShortenerServiceTest {

	@Mock
	private TsidGenerator tsidGenerator;

	@Mock
	private URLShortenerJpaRepository urlShortenerJpaRepository;

	@Mock
	private Base62Encoder base62Encoder;

	@Mock
	private HashGenerator hashGenerator;

	@InjectMocks
	private URLShortenerService urlShortenerService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(urlShortenerService, "redirectionBaseDomain", "http://localhost:8080");
		ReflectionTestUtils.setField(urlShortenerService, "defaultExpirationDays", 7);
		ReflectionTestUtils.setField(urlShortenerService, "hashKeySize", 8);
		ReflectionTestUtils.setField(urlShortenerService, "retry", 3);
	}

	@Nested
	@DisplayName("createLink 테스트")
	class CreateLinkTest {

		@Test
		@DisplayName("성공: 첫 시도에 단축 URL 생성")
		void createLink_success_firstTry() {
			// given
			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};
			String encodedHash = "aB3Xy9KmP2qLnR5vT8wC";

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(tsid, redirectUrl)).thenReturn(hash);
			when(base62Encoder.encode(hash)).thenReturn(encodedHash);
			when(urlShortenerJpaRepository.save(any(URLShortener.class))).thenAnswer(i -> i.getArgument(0));

			// when
			LinkCreateResponse response = urlShortenerService.createLink(redirectUrl);

			// then
			assertNotNull(response);
			assertEquals(8, response.hashKey().length());
			assertTrue(response.url().contains(response.hashKey()));
			verify(tsidGenerator, times(1)).nextKey();
			verify(urlShortenerJpaRepository, times(1)).save(any(URLShortener.class));
		}

		@Test
		@DisplayName("성공: 첫 시도 충돌 후 두 번째 시도에 성공")
		void createLink_success_afterCollision() {
			// given
			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};
			String encodedHash = "aB3Xy9KmP2qLnR5vT8wC";

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(tsid, redirectUrl)).thenReturn(hash);
			when(base62Encoder.encode(hash)).thenReturn(encodedHash);

			// 첫 번째 시도: 충돌
			when(urlShortenerJpaRepository.save(any(URLShortener.class)))
				.thenThrow(new DataIntegrityViolationException("Duplicate key"))
				.thenAnswer(i -> i.getArgument(0));

			// when
			LinkCreateResponse response = urlShortenerService.createLink(redirectUrl);

			// then
			assertNotNull(response);
			verify(urlShortenerJpaRepository, times(2)).save(any(URLShortener.class));
		}

		@Test
		@DisplayName("실패: 재시도 횟수 초과 시 URL_GENERATION_FAILED 예외")
		void createLink_failAfterRetries() {
			// given
			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};
			String encodedHash = "aB3Xy9KmP2qLnR5vT8wC";

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(tsid, redirectUrl)).thenReturn(hash);
			when(base62Encoder.encode(hash)).thenReturn(encodedHash);

			// 모든 시도 충돌
			when(urlShortenerJpaRepository.save(any(URLShortener.class)))
				.thenThrow(new DataIntegrityViolationException("Duplicate key"));

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> urlShortenerService.createLink(redirectUrl));

			assertEquals(ErrorCode.URL_GENERATION_FAILED.getMessage(), exception.getMessage());
			verify(urlShortenerJpaRepository, times(3)).save(any(URLShortener.class));
		}

		@Test
		@DisplayName("실패: 요청 취소 시 REQUEST_CANCELLED 예외")
		void createLink_requestCancelled() {
			// given
			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};
			String encodedHash = "aB3Xy9KmP2qLnR5vT8wC";

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(tsid, redirectUrl)).thenReturn(hash);
			when(base62Encoder.encode(hash)).thenReturn(encodedHash);

			Context.CancellableContext ctx = Context.current().withCancellation();
			ctx.cancel(new RuntimeException("cancelled"));

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> ctx.call(() -> urlShortenerService.createLink(redirectUrl)));

			assertEquals(ErrorCode.REQUEST_CANCELLED.getMessage(), exception.getMessage());
			verify(urlShortenerJpaRepository, never()).save(any(URLShortener.class));
		}

		@Test
		@DisplayName("성공: Base62 인코딩 결과에서 8글자 부분 문자열 추출")
		void createLink_extractsCorrectLength() {
			// given
			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};
			String encodedHash = "aB3Xy9KmP2qLnR5vT8wC"; // 20글자

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(tsid, redirectUrl)).thenReturn(hash);
			when(base62Encoder.encode(hash)).thenReturn(encodedHash);
			when(urlShortenerJpaRepository.save(any(URLShortener.class))).thenAnswer(i -> i.getArgument(0));

			// when
			LinkCreateResponse response = urlShortenerService.createLink(redirectUrl);

			// then
			assertEquals(8, response.hashKey().length());
			assertTrue(encodedHash.contains(response.hashKey()));
		}
	}

	@Nested
	@DisplayName("getLink 테스트")
	class GetLinkTest {

		@Test
		@DisplayName("성공: 유효한 키로 리다이렉션 URL 조회")
		void getLink_success() {
			// given
			String hashKey = "aB3Xy9Km";
			String redirectUrl = "https://example.com";
			URLShortener urlShortener = new URLShortener(
				123456789L,
				hashKey,
				redirectUrl,
				LocalDateTime.now().plusDays(7)
			);

			when(base62Encoder.isValid(hashKey)).thenReturn(true);
			when(urlShortenerJpaRepository.findByHashKey(hashKey)).thenReturn(Optional.of(urlShortener));

			// when
			String result = urlShortenerService.getLink(hashKey);

			// then
			assertEquals(redirectUrl, result);
			verify(urlShortenerJpaRepository, times(1)).findByHashKey(hashKey);
		}

		@Test
		@DisplayName("실패: 존재하지 않는 키 조회 시 KEY_NOT_FOUND 예외")
		void getLink_keyNotFound() {
			// given
			String hashKey = "notExist";

			when(base62Encoder.isValid(hashKey)).thenReturn(true);
			when(urlShortenerJpaRepository.findByHashKey(hashKey)).thenReturn(Optional.empty());

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> urlShortenerService.getLink(hashKey));

			assertEquals(ErrorCode.KEY_NOT_FOUND.getMessage(), exception.getMessage());
		}

		@Test
		@DisplayName("실패: 만료된 링크 조회 시 EXPIRED_LINK 예외")
		void getLink_expiredLink() {
			// given
			String hashKey = "expired1";
			String redirectUrl = "https://example.com";
			URLShortener urlShortener = new URLShortener(
				123456789L,
				hashKey,
				redirectUrl,
				LocalDateTime.now().minusDays(1) // 이미 만료됨
			);

			when(base62Encoder.isValid(hashKey)).thenReturn(true);
			when(urlShortenerJpaRepository.findByHashKey(hashKey)).thenReturn(Optional.of(urlShortener));

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> urlShortenerService.getLink(hashKey));

			assertEquals(ErrorCode.EXPIRED_LINK.getMessage(), exception.getMessage());
		}

		@Test
		@DisplayName("실패: 잘못된 키 형식 시 INVALID_KEY_ERROR 예외")
		void getLink_invalidKeyFormat() {
			// given
			String hashKey = "invalid@key!";

			when(base62Encoder.isValid(hashKey)).thenReturn(false);

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> urlShortenerService.getLink(hashKey));

			assertEquals(ErrorCode.INVALID_KEY_ERROR.getMessage(), exception.getMessage());
			verify(urlShortenerJpaRepository, never()).findByHashKey(anyString());
		}
	}

	@Nested
	@DisplayName("deleteLink 테스트")
	class DeleteLinkTest {

		@Test
		@DisplayName("성공: 유효한 키로 삭제")
		void deleteLink_success() {
			// given
			String hashKey = "aB3Xy9Km";

			when(base62Encoder.isValid(hashKey)).thenReturn(true);
			when(urlShortenerJpaRepository.deleteByHashKey(hashKey)).thenReturn(1);

			// when
			urlShortenerService.deleteLink(hashKey);

			// then
			verify(base62Encoder, times(1)).isValid(hashKey);
			verify(urlShortenerJpaRepository, times(1)).deleteByHashKey(hashKey);
		}

		@Test
		@DisplayName("실패: 잘못된 키 형식 시 INVALID_KEY_ERROR 예외")
		void deleteLink_invalidKey() {
			// given
			String hashKey = "invalid@key!";

			when(base62Encoder.isValid(hashKey)).thenReturn(false);

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> urlShortenerService.deleteLink(hashKey));

			assertEquals(ErrorCode.INVALID_KEY_ERROR.getMessage(), exception.getMessage());
			verify(urlShortenerJpaRepository, never()).deleteByHashKey(anyString());
		}

		@Test
		@DisplayName("성공: 존재하지 않는 키 삭제 시도 (에러 없음)")
		void deleteLink_nonExistentKey() {
			// given
			String hashKey = "notExist";

			when(base62Encoder.isValid(hashKey)).thenReturn(true);
			when(urlShortenerJpaRepository.deleteByHashKey(hashKey)).thenReturn(0);

			// when & then (예외 발생하지 않아야 함)
			assertDoesNotThrow(() -> urlShortenerService.deleteLink(hashKey));
			verify(urlShortenerJpaRepository, times(1)).deleteByHashKey(hashKey);
		}
	}

	@Nested
	@DisplayName("URL 생성 테스트")
	class UrlGenerationTest {

		@Test
		@DisplayName("성공: 리다이렉션 도메인으로 단축 URL 생성")
		void toShortUrl_withTrailingSlash() {
			// given
			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};
			String encodedHash = "aB3Xy9KmP2qLnR5vT8wC";

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(tsid, redirectUrl)).thenReturn(hash);
			when(base62Encoder.encode(hash)).thenReturn(encodedHash);
			when(urlShortenerJpaRepository.save(any(URLShortener.class))).thenAnswer(i -> i.getArgument(0));

			// when
			LinkCreateResponse response = urlShortenerService.createLink(redirectUrl);

			// then
			assertTrue(response.url().startsWith("http://localhost:8080/"));
			assertEquals("http://localhost:8080/" + response.hashKey(), response.url());
		}

		@Test
		@DisplayName("성공: 도메인에 이미 슬래시가 있으면 중복 슬래시 없음")
		void toShortUrl_alreadyHasTrailingSlash() {
			// given
			ReflectionTestUtils.setField(urlShortenerService, "redirectionBaseDomain", "http://localhost:8080/");

			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};
			String encodedHash = "aB3Xy9KmP2qLnR5vT8wC";

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(tsid, redirectUrl)).thenReturn(hash);
			when(base62Encoder.encode(hash)).thenReturn(encodedHash);
			when(urlShortenerJpaRepository.save(any(URLShortener.class))).thenAnswer(i -> i.getArgument(0));

			// when
			LinkCreateResponse response = urlShortenerService.createLink(redirectUrl);

			// then
			assertEquals("http://localhost:8080/" + response.hashKey(), response.url());
			assertFalse(response.url().contains("//" + response.hashKey()));
		}

		@Test
		@DisplayName("성공: 도메인에 슬래시가 없어도 자동 추가")
		void toShortUrl_withoutTrailingSlash() {
			// given
			ReflectionTestUtils.setField(urlShortenerService, "redirectionBaseDomain", "http://localhost:8080");

			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};
			String encodedHash = "aB3Xy9KmP2qLnR5vT8wC";

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(tsid, redirectUrl)).thenReturn(hash);
			when(base62Encoder.encode(hash)).thenReturn(encodedHash);
			when(urlShortenerJpaRepository.save(any(URLShortener.class))).thenAnswer(i -> i.getArgument(0));

			// when
			LinkCreateResponse response = urlShortenerService.createLink(redirectUrl);

			// then
			assertTrue(response.url().startsWith("http://localhost:8080/"));
			assertFalse(response.url().contains("//aB")); // 이중 슬래시 없어야 함
		}
	}
}
