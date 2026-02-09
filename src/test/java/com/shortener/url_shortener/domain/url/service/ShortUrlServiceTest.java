package com.shortener.url_shortener.domain.url.service;

import com.shortener.url_shortener.domain.url.dto.response.ShortUrlCreateResponse;
import com.shortener.url_shortener.domain.url.entity.ShortUrl;
import com.shortener.url_shortener.domain.url.repository.ShortUrlJpaRepository;
import com.shortener.url_shortener.domain.url.repository.ShortUrlLockRepository;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
class ShortUrlServiceTest {

	@Mock
	private TsidGenerator tsidGenerator;

	@Mock
	private ShortUrlJpaRepository shortUrlJpaRepository;

	@Mock
	private Base62Encoder base62Encoder;

	@Mock
	private HashGenerator hashGenerator;

	@Mock
	private ShortUrlLockRepository shortUrlLockRepository;

	@InjectMocks
	private ShortUrlService shortUrlService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(shortUrlService, "redirectionBaseDomain", "http://localhost:8080");
		ReflectionTestUtils.setField(shortUrlService, "defaultExpirationDays", 7);
		ReflectionTestUtils.setField(shortUrlService, "hashKeySize", 8);
		ReflectionTestUtils.setField(shortUrlService, "retry", 3);
		ReflectionTestUtils.setField(shortUrlService, "maxUrlLength", 2048);
		ReflectionTestUtils.setField(shortUrlService, "lockTimeoutSeconds", 3);

		lenient().when(shortUrlLockRepository.acquireLock(anyString(), anyInt()))
			.thenReturn(true);
		lenient().doNothing().when(shortUrlLockRepository).releaseLock(anyString());
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
			String shortCode = "aB3Xy9Km";

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(redirectUrl)).thenReturn(hash);
			when(base62Encoder.random(eq(8), any(SecureRandom.class))).thenReturn(shortCode);
			when(shortUrlJpaRepository.findByHashKeyAndExpiredAtAfter(eq(hash), any(LocalDateTime.class)))
				.thenReturn(List.of());
			when(shortUrlJpaRepository.save(any(ShortUrl.class))).thenAnswer(i -> i.getArgument(0));

			// when
			ShortUrlCreateResponse response = shortUrlService.createLink(redirectUrl);

			// then
			assertNotNull(response);
			assertEquals(8, response.shortCode().length());
			assertTrue(response.url().contains(response.shortCode()));
			verify(tsidGenerator, times(1)).nextKey();
			verify(shortUrlJpaRepository, times(1)).save(any(ShortUrl.class));
		}

		@Test
		@DisplayName("성공: 동일 URL이 이미 존재하면 기존 shortCode 반환")
		void createLink_existingUrl_returnsExistingShortCode() {
			// given
			String redirectUrl = "https://example.com";
			byte[] hash = new byte[]{1, 2, 3, 4};
			String existingShortCode = "aB3Xy9Km";
			ShortUrl existing = new ShortUrl(
				123456789L,
				hash,
				existingShortCode,
				redirectUrl,
				LocalDateTime.now().plusDays(7)
			);

			when(hashGenerator.hash(redirectUrl)).thenReturn(hash);
			when(shortUrlJpaRepository.findByHashKeyAndExpiredAtAfter(eq(hash), any(LocalDateTime.class)))
				.thenReturn(List.of(existing));

			// when
			ShortUrlCreateResponse response = shortUrlService.createLink(redirectUrl);

			// then
			assertEquals(existingShortCode, response.shortCode());
			assertEquals("http://localhost:8080/" + existingShortCode, response.url());
			verify(tsidGenerator, never()).nextKey();
			verify(shortUrlJpaRepository, never()).save(any(ShortUrl.class));
		}

		@Test
		@DisplayName("성공: 첫 시도 충돌 후 두 번째 시도에 성공")
		void createLink_success_afterCollision() {
			// given
			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};
			String shortCode1 = "aB3Xy9Km";
			String shortCode2 = "bB3Xy9Km";

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(redirectUrl)).thenReturn(hash);
			when(base62Encoder.random(eq(8), any(SecureRandom.class)))
				.thenReturn(shortCode1, shortCode2);
			when(shortUrlJpaRepository.findByHashKeyAndExpiredAtAfter(eq(hash), any(LocalDateTime.class)))
				.thenReturn(List.of());

			// 첫 번째 시도: 충돌
			when(shortUrlJpaRepository.save(any(ShortUrl.class)))
				.thenThrow(new DataIntegrityViolationException("Duplicate key"))
				.thenAnswer(i -> i.getArgument(0));

			// when
			ShortUrlCreateResponse response = shortUrlService.createLink(redirectUrl);

			// then
			assertNotNull(response);
			verify(shortUrlJpaRepository, times(2)).save(any(ShortUrl.class));
		}

		@Test
		@DisplayName("실패: 재시도 횟수 초과 시 URL_GENERATION_FAILED 예외")
		void createLink_failAfterRetries() {
			// given
			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(redirectUrl)).thenReturn(hash);
			when(shortUrlJpaRepository.findByHashKeyAndExpiredAtAfter(eq(hash), any(LocalDateTime.class)))
				.thenReturn(List.of());

			// 모든 시도 충돌
			when(shortUrlJpaRepository.save(any(ShortUrl.class)))
				.thenThrow(new DataIntegrityViolationException("Duplicate key"));

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> shortUrlService.createLink(redirectUrl));

			assertEquals(ErrorCode.URL_GENERATION_FAILED.getMessage(), exception.getMessage());
			verify(shortUrlJpaRepository, times(3)).save(any(ShortUrl.class));
		}

		@Test
		@DisplayName("실패: 요청 취소 시 REQUEST_CANCELLED 예외")
		void createLink_requestCancelled() {
			// given
			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(redirectUrl)).thenReturn(hash);
			when(shortUrlJpaRepository.findByHashKeyAndExpiredAtAfter(eq(hash), any(LocalDateTime.class)))
				.thenReturn(List.of());

			Context.CancellableContext ctx = Context.current().withCancellation();
			ctx.cancel(new RuntimeException("cancelled"));

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> ctx.call(() -> shortUrlService.createLink(redirectUrl)));

			assertEquals(ErrorCode.REQUEST_CANCELLED.getMessage(), exception.getMessage());
			verify(shortUrlJpaRepository, never()).save(any(ShortUrl.class));
		}

		@Test
		@DisplayName("실패: URL이 null이면 INVALID_ARGUMENT_ERROR 예외")
		void createLink_nullUrl() {
			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> shortUrlService.createLink(null));

			assertEquals(ErrorCode.INVALID_ARGUMENT_ERROR.getMessage(), exception.getMessage());
		}

		@Test
		@DisplayName("실패: URL이 빈 문자열이면 INVALID_ARGUMENT_ERROR 예외")
		void createLink_blankUrl() {
			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> shortUrlService.createLink("  "));

			assertEquals(ErrorCode.INVALID_ARGUMENT_ERROR.getMessage(), exception.getMessage());
		}

		@Test
		@DisplayName("실패: 지원하지 않는 스킴이면 INVALID_ARGUMENT_ERROR 예외")
		void createLink_invalidScheme() {
			// given
			String redirectUrl = "ftp://example.com";

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> shortUrlService.createLink(redirectUrl));

			assertEquals(ErrorCode.INVALID_ARGUMENT_ERROR.getMessage(), exception.getMessage());
		}

		@Test
		@DisplayName("실패: 호스트 정보가 없으면 INVALID_ARGUMENT_ERROR 예외")
		void createLink_missingHost() {
			// given
			String redirectUrl = "http:///path-only";

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> shortUrlService.createLink(redirectUrl));

			assertEquals(ErrorCode.INVALID_ARGUMENT_ERROR.getMessage(), exception.getMessage());
		}

		@Test
		@DisplayName("실패: URL 형식 오류면 INVALID_ARGUMENT_ERROR 예외")
		void createLink_invalidUriSyntax() {
			// given
			String redirectUrl = "http://exa mple.com";

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> shortUrlService.createLink(redirectUrl));

			assertEquals(ErrorCode.INVALID_ARGUMENT_ERROR.getMessage(), exception.getMessage());
		}

		@Test
		@DisplayName("실패: 락 획득 실패 시 URL_GENERATION_FAILED 예외")
		void createLink_lockAcquireFail() {
			// given
			String redirectUrl = "https://example.com";
			byte[] hash = new byte[]{1, 2, 3, 4};
			when(hashGenerator.hash(redirectUrl)).thenReturn(hash);
			when(shortUrlLockRepository.acquireLock(anyString(), anyInt())).thenReturn(false);

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> shortUrlService.createLink(redirectUrl));

			assertEquals(ErrorCode.URL_GENERATION_FAILED.getMessage(), exception.getMessage());
		}

		@Test
		@DisplayName("실패: URL 길이가 제한을 초과하면 INVALID_ARGUMENT_ERROR 예외")
		void createLink_exceedsMaxLength() {
			// given
			String longPath = "a".repeat(2100);
			String redirectUrl = "http://example.com/" + longPath;

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> shortUrlService.createLink(redirectUrl));

			assertEquals(ErrorCode.INVALID_ARGUMENT_ERROR.getMessage(), exception.getMessage());
		}

		@Test
		@DisplayName("성공: 랜덤 shortCode 길이 8 생성")
		void createLink_generatesShortCodeWithLength() {
			// given
			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};
			String shortCode = "aB3Xy9Km";

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(redirectUrl)).thenReturn(hash);
			when(base62Encoder.random(eq(8), any(SecureRandom.class))).thenReturn(shortCode);
			when(shortUrlJpaRepository.findByHashKeyAndExpiredAtAfter(eq(hash), any(LocalDateTime.class)))
				.thenReturn(List.of());
			when(shortUrlJpaRepository.save(any(ShortUrl.class))).thenAnswer(i -> i.getArgument(0));

			// when
			ShortUrlCreateResponse response = shortUrlService.createLink(redirectUrl);

			// then
			assertEquals(8, response.shortCode().length());
			assertEquals(shortCode, response.shortCode());
		}

		@Test
		@DisplayName("락 해제는 트랜잭션 완료 이후 수행")
		void createLink_releasesLockAfterCompletion() {
			// given
			String redirectUrl = "https://example.com";
			byte[] hash = new byte[]{1, 2, 3, 4};
			ShortUrl existing = new ShortUrl(1L, hash, "aB3Xy9Km", redirectUrl, LocalDateTime.now().plusDays(1));

			when(hashGenerator.hash(redirectUrl)).thenReturn(hash);
			when(shortUrlJpaRepository.findByHashKeyAndExpiredAtAfter(eq(hash), any(LocalDateTime.class)))
				.thenReturn(List.of(existing));

			TransactionSynchronizationManager.initSynchronization();
			try {
				// when
				shortUrlService.createLink(redirectUrl);

				// then: 트랜잭션 완료 전에는 락 해제하지 않음
				verify(shortUrlLockRepository, never()).releaseLock(anyString());

				var synchronizations = TransactionSynchronizationManager.getSynchronizations();
				assertEquals(1, synchronizations.size());

				synchronizations.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
				verify(shortUrlLockRepository).releaseLock(anyString());
			} finally {
				TransactionSynchronizationManager.clearSynchronization();
			}
		}
	}

	@Nested
	@DisplayName("getLink 테스트")
	class GetLinkTest {

		@Test
		@DisplayName("성공: 유효한 키로 리다이렉션 URL 조회")
		void getLink_success() {
			// given
			String shortCode = "aB3Xy9Km";
			String redirectUrl = "https://example.com";
			ShortUrl shortUrl = new ShortUrl(
				123456789L,
				new byte[]{1, 2, 3, 4},
				shortCode,
				redirectUrl,
				LocalDateTime.now().plusDays(7)
			);

			when(base62Encoder.isValid(shortCode)).thenReturn(true);
			when(shortUrlJpaRepository.findByShortCode(shortCode)).thenReturn(Optional.of(shortUrl));

			// when
			String result = shortUrlService.getLink(shortCode);

			// then
			assertEquals(redirectUrl, result);
			verify(shortUrlJpaRepository, times(1)).findByShortCode(shortCode);
		}

		@Test
		@DisplayName("실패: 존재하지 않는 키 조회 시 KEY_NOT_FOUND 예외")
		void getLink_keyNotFound() {
			// given
			String shortCode = "notExist";

			when(base62Encoder.isValid(shortCode)).thenReturn(true);
			when(shortUrlJpaRepository.findByShortCode(shortCode)).thenReturn(Optional.empty());

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> shortUrlService.getLink(shortCode));

			assertEquals(ErrorCode.KEY_NOT_FOUND.getMessage(), exception.getMessage());
		}

		@Test
		@DisplayName("실패: 만료된 링크 조회 시 EXPIRED_LINK 예외")
		void getLink_expiredLink() {
			// given
			String shortCode = "expired1";
			String redirectUrl = "https://example.com";
			ShortUrl shortUrl = new ShortUrl(
				123456789L,
				new byte[]{1, 2, 3, 4},
				shortCode,
				redirectUrl,
				LocalDateTime.now().minusDays(1) // 이미 만료됨
			);

			when(base62Encoder.isValid(shortCode)).thenReturn(true);
			when(shortUrlJpaRepository.findByShortCode(shortCode)).thenReturn(Optional.of(shortUrl));

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> shortUrlService.getLink(shortCode));

			assertEquals(ErrorCode.EXPIRED_LINK.getMessage(), exception.getMessage());
		}

		@Test
		@DisplayName("실패: 잘못된 키 형식 시 INVALID_KEY_ERROR 예외")
		void getLink_invalidKeyFormat() {
			// given
			String shortCode = "invalid@key!";

			when(base62Encoder.isValid(shortCode)).thenReturn(false);

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> shortUrlService.getLink(shortCode));

			assertEquals(ErrorCode.INVALID_KEY_ERROR.getMessage(), exception.getMessage());
			verify(shortUrlJpaRepository, never()).findByShortCode(anyString());
		}
	}

	@Nested
	@DisplayName("deleteLink 테스트")
	class DeleteLinkTest {

		@Test
		@DisplayName("성공: 유효한 키로 삭제")
		void deleteLink_success() {
			// given
			String shortCode = "aB3Xy9Km";

			when(base62Encoder.isValid(shortCode)).thenReturn(true);
			when(shortUrlJpaRepository.deleteByShortCode(shortCode)).thenReturn(1);

			// when
			shortUrlService.deleteLink(shortCode);

			// then
			verify(base62Encoder, times(1)).isValid(shortCode);
			verify(shortUrlJpaRepository, times(1)).deleteByShortCode(shortCode);
		}

		@Test
		@DisplayName("실패: 잘못된 키 형식 시 INVALID_KEY_ERROR 예외")
		void deleteLink_invalidKey() {
			// given
			String shortCode = "invalid@key!";

			when(base62Encoder.isValid(shortCode)).thenReturn(false);

			// when & then
			CustomException exception = assertThrows(CustomException.class,
				() -> shortUrlService.deleteLink(shortCode));

			assertEquals(ErrorCode.INVALID_KEY_ERROR.getMessage(), exception.getMessage());
			verify(shortUrlJpaRepository, never()).deleteByShortCode(anyString());
		}

		@Test
		@DisplayName("성공: 존재하지 않는 키 삭제 시도 (에러 없음)")
		void deleteLink_nonExistentKey() {
			// given
			String shortCode = "notExist";

			when(base62Encoder.isValid(shortCode)).thenReturn(true);
			when(shortUrlJpaRepository.deleteByShortCode(shortCode)).thenReturn(0);

			// when & then (예외 발생하지 않아야 함)
			assertDoesNotThrow(() -> shortUrlService.deleteLink(shortCode));
			verify(shortUrlJpaRepository, times(1)).deleteByShortCode(shortCode);
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
			String shortCode = "aB3Xy9Km";

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(redirectUrl)).thenReturn(hash);
			when(base62Encoder.random(eq(8), any(SecureRandom.class))).thenReturn(shortCode);
			when(shortUrlJpaRepository.findByHashKeyAndExpiredAtAfter(eq(hash), any(LocalDateTime.class)))
				.thenReturn(List.of());
			when(shortUrlJpaRepository.save(any(ShortUrl.class))).thenAnswer(i -> i.getArgument(0));

			// when
			ShortUrlCreateResponse response = shortUrlService.createLink(redirectUrl);

			// then
			assertTrue(response.url().startsWith("http://localhost:8080/"));
			assertEquals("http://localhost:8080/" + response.shortCode(), response.url());
		}

		@Test
		@DisplayName("성공: 도메인에 이미 슬래시가 있으면 중복 슬래시 없음")
		void toShortUrl_alreadyHasTrailingSlash() {
			// given
			ReflectionTestUtils.setField(shortUrlService, "redirectionBaseDomain", "http://localhost:8080/");

			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};
			String shortCode = "aB3Xy9Km";

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(redirectUrl)).thenReturn(hash);
			when(base62Encoder.random(eq(8), any(SecureRandom.class))).thenReturn(shortCode);
			when(shortUrlJpaRepository.findByHashKeyAndExpiredAtAfter(eq(hash), any(LocalDateTime.class)))
				.thenReturn(List.of());
			when(shortUrlJpaRepository.save(any(ShortUrl.class))).thenAnswer(i -> i.getArgument(0));

			// when
			ShortUrlCreateResponse response = shortUrlService.createLink(redirectUrl);

			// then
			assertEquals("http://localhost:8080/" + response.shortCode(), response.url());
			assertFalse(response.url().contains("//" + response.shortCode()));
		}

		@Test
		@DisplayName("성공: 도메인에 슬래시가 없어도 자동 추가")
		void toShortUrl_withoutTrailingSlash() {
			// given
			ReflectionTestUtils.setField(shortUrlService, "redirectionBaseDomain", "http://localhost:8080");

			String redirectUrl = "https://example.com";
			Long tsid = 123456789L;
			byte[] hash = new byte[]{1, 2, 3, 4};
			String shortCode = "aB3Xy9Km";

			when(tsidGenerator.nextKey()).thenReturn(tsid);
			when(hashGenerator.hash(redirectUrl)).thenReturn(hash);
			when(base62Encoder.random(eq(8), any(SecureRandom.class))).thenReturn(shortCode);
			when(shortUrlJpaRepository.findByHashKeyAndExpiredAtAfter(eq(hash), any(LocalDateTime.class)))
				.thenReturn(List.of());
			when(shortUrlJpaRepository.save(any(ShortUrl.class))).thenAnswer(i -> i.getArgument(0));

			// when
			ShortUrlCreateResponse response = shortUrlService.createLink(redirectUrl);

			// then
			assertTrue(response.url().startsWith("http://localhost:8080/"));
			assertFalse(response.url().contains("//aB")); // 이중 슬래시 없어야 함
		}
	}
}
