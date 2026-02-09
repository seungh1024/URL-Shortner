package com.shortener.url_shortener.domain.url.controller;

import com.shortener.url_shortener.container.IntegrationTestBase;
import com.shortener.url_shortener.domain.url.CreateLinkRequest;
import com.shortener.url_shortener.domain.url.CreateLinkResponse;
import com.shortener.url_shortener.domain.url.DeleteLinkRequest;
import com.shortener.url_shortener.domain.url.DeleteLinkResponse;
import com.shortener.url_shortener.domain.url.UrlShortenerRpcGrpc;
import com.shortener.url_shortener.domain.url.entity.ShortUrl;
import com.shortener.url_shortener.domain.url.repository.ShortUrlJpaRepository;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UrlShortenerGrpc 통합 테스트
 * 
 * 테스트 내용:
 * - 실제 gRPC 서버-클라이언트 통신
 * - API Key 인증 통합 테스트
 * - DB 연동 테스트 (테스트컨테이너)
 * - 전체 흐름 검증
 */
@DisplayName("UrlShortenerGrpc 통합 테스트")
@TestPropertySource(properties = "grpc.server.enabled=true")
class UrlShortenerGrpcIntegrationTest extends IntegrationTestBase {

	@GrpcClient("url-shortener")
	private UrlShortenerRpcGrpc.UrlShortenerRpcBlockingStub blockingStub;

	@Autowired
	private ShortUrlJpaRepository shortUrlJpaRepository;

	@Value("${grpc.server.api-key}")
	private String validApiKey;

	private static final Metadata.Key<String> API_KEY_METADATA_KEY =
		Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

	/**
	 * API Key를 헤더에 추가한 Stub 생성
	 */
	private UrlShortenerRpcGrpc.UrlShortenerRpcBlockingStub createAuthenticatedStub() {
		Metadata metadata = new Metadata();
		metadata.put(API_KEY_METADATA_KEY, validApiKey);
		return blockingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
	}

	@Nested
	@DisplayName("createLink 통합 테스트")
	class CreateLinkIntegrationTest {

		@Test
		@DisplayName("성공: API Key 인증 + DB 저장 + 응답 반환")
		void createLink_fullFlow_success() {
			// given
			String redirectUrl = "https://example.com/test";
			CreateLinkRequest request = CreateLinkRequest.newBuilder()
				.setRedirectUrl(redirectUrl)
				.build();

			UrlShortenerRpcGrpc.UrlShortenerRpcBlockingStub authenticatedStub = createAuthenticatedStub();

			// when
			CreateLinkResponse response = authenticatedStub.createLink(request);

			// then
			assertNotNull(response);
			assertNotNull(response.getShortCode());
			assertEquals(8, response.getShortCode().length());
			assertTrue(response.getShortUrl().contains(response.getShortCode()));

			// DB 저장 확인
			Optional<ShortUrl> saved = shortUrlJpaRepository.findByShortCode(response.getShortCode());
			assertTrue(saved.isPresent());
			assertEquals(redirectUrl, saved.get().getRedirectionUrl());
			assertFalse(saved.get().isExpired());
		}

		@Test
		@DisplayName("실패: API Key 없이 요청 시 UNAUTHENTICATED 에러")
		void createLink_withoutApiKey_unauthenticated() {
			// given
			CreateLinkRequest request = CreateLinkRequest.newBuilder()
				.setRedirectUrl("https://example.com")
				.build();

			// when & then
			StatusRuntimeException exception = assertThrows(StatusRuntimeException.class,
				() -> blockingStub.createLink(request));

			assertEquals(Status.Code.UNAUTHENTICATED, exception.getStatus().getCode());
			assertTrue(exception.getStatus().getDescription().contains("API Key"));
		}

		@Test
		@DisplayName("실패: 잘못된 API Key로 요청 시 UNAUTHENTICATED 에러")
		void createLink_withInvalidApiKey_unauthenticated() {
			// given
			Metadata metadata = new Metadata();
			metadata.put(API_KEY_METADATA_KEY, "wrong-api-key");

			UrlShortenerRpcGrpc.UrlShortenerRpcBlockingStub wrongStub =
				blockingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

			CreateLinkRequest request = CreateLinkRequest.newBuilder()
				.setRedirectUrl("https://example.com")
				.build();

			// when & then
			StatusRuntimeException exception = assertThrows(StatusRuntimeException.class,
				() -> wrongStub.createLink(request));

			assertEquals(Status.Code.UNAUTHENTICATED, exception.getStatus().getCode());
			assertTrue(exception.getStatus().getDescription().contains("Invalid API Key"));
		}

		@Test
		@DisplayName("성공: 동일한 URL로 여러 번 요청 시 동일한 shortCode 반환")
		void createLink_sameUrl_sameCode() {
			// given
			String redirectUrl = "https://example.com/same";
			CreateLinkRequest request = CreateLinkRequest.newBuilder()
				.setRedirectUrl(redirectUrl)
				.build();

			UrlShortenerRpcGrpc.UrlShortenerRpcBlockingStub authenticatedStub = createAuthenticatedStub();

			// when
			CreateLinkResponse response1 = authenticatedStub.createLink(request);
			CreateLinkResponse response2 = authenticatedStub.createLink(request);

			// then
			assertEquals(response1.getShortCode(), response2.getShortCode());
			assertEquals(1, shortUrlJpaRepository.count());
		}
	}

	@Nested
	@DisplayName("deleteLink 통합 테스트")
	class DeleteLinkIntegrationTest {

		@Test
		@DisplayName("성공: API Key 인증 + DB 삭제")
		void deleteLink_fullFlow_success() {
			// given
			String shortCode = "testKey1";
			ShortUrl entity = new ShortUrl(
				12345L,
				shortCode,
				"https://example.com",
				LocalDateTime.now().plusDays(7)
			);
			shortUrlJpaRepository.save(entity);

			DeleteLinkRequest request = DeleteLinkRequest.newBuilder()
				.setShortCode(shortCode)
				.build();

			UrlShortenerRpcGrpc.UrlShortenerRpcBlockingStub authenticatedStub = createAuthenticatedStub();

			// when
			DeleteLinkResponse response = authenticatedStub.deleteLink(request);

			// then
			assertNotNull(response);

			// DB 삭제 확인
			Optional<ShortUrl> deleted = shortUrlJpaRepository.findByShortCode(shortCode);
			assertFalse(deleted.isPresent());
		}

		@Test
		@DisplayName("실패: API Key 없이 요청 시 UNAUTHENTICATED 에러")
		void deleteLink_withoutApiKey_unauthenticated() {
			// given
			DeleteLinkRequest request = DeleteLinkRequest.newBuilder()
				.setShortCode("testKey")
				.build();

			// when & then
			StatusRuntimeException exception = assertThrows(StatusRuntimeException.class,
				() -> blockingStub.deleteLink(request));

			assertEquals(Status.Code.UNAUTHENTICATED, exception.getStatus().getCode());
		}

		@Test
		@DisplayName("성공: 존재하지 않는 키 삭제 시 에러 없음")
		void deleteLink_nonExistentKey_noError() {
			// given
			DeleteLinkRequest request = DeleteLinkRequest.newBuilder()
				.setShortCode("notExist")
				.build();

			UrlShortenerRpcGrpc.UrlShortenerRpcBlockingStub authenticatedStub = createAuthenticatedStub();

			// when & then (예외 발생하지 않아야 함)
			assertDoesNotThrow(() -> authenticatedStub.deleteLink(request));
		}

		@Test
		@DisplayName("실패: 잘못된 키 형식 시 INVALID_ARGUMENT 에러")
		void deleteLink_invalidKey_invalidArgument() {
			// given
			DeleteLinkRequest request = DeleteLinkRequest.newBuilder()
				.setShortCode("invalid@key!")
				.build();

			UrlShortenerRpcGrpc.UrlShortenerRpcBlockingStub authenticatedStub = createAuthenticatedStub();

			// when & then
			StatusRuntimeException exception = assertThrows(StatusRuntimeException.class,
				() -> authenticatedStub.deleteLink(request));

			assertEquals(Status.Code.INVALID_ARGUMENT, exception.getStatus().getCode());
		}
	}

	@Nested
	@DisplayName("전체 시나리오 테스트")
	class FullScenarioTest {

		@Test
		@DisplayName("성공: 생성 → 조회 → 삭제 전체 흐름")
		void fullScenario_createQueryDelete() {
			// given
			String redirectUrl = "https://example.com/scenario";
			UrlShortenerRpcGrpc.UrlShortenerRpcBlockingStub authenticatedStub = createAuthenticatedStub();

			// 1. 생성
			CreateLinkRequest createRequest = CreateLinkRequest.newBuilder()
				.setRedirectUrl(redirectUrl)
				.build();
			CreateLinkResponse createResponse = authenticatedStub.createLink(createRequest);
			String shortCode = createResponse.getShortCode();

			// 2. 조회 (DB에서 직접)
			Optional<ShortUrl> queried = shortUrlJpaRepository.findByShortCode(shortCode);
			assertTrue(queried.isPresent());
			assertEquals(redirectUrl, queried.get().getRedirectionUrl());

			// 3. 삭제
			DeleteLinkRequest deleteRequest = DeleteLinkRequest.newBuilder()
				.setShortCode(shortCode)
				.build();
			DeleteLinkResponse deleteResponse = authenticatedStub.deleteLink(deleteRequest);
			assertNotNull(deleteResponse);

			// 4. 삭제 확인
			Optional<ShortUrl> afterDelete = shortUrlJpaRepository.findByShortCode(shortCode);
			assertFalse(afterDelete.isPresent());
		}

		@Test
		@DisplayName("성공: 여러 링크 생성 후 일부만 삭제")
		void multipleLinks_partialDelete() {
			// given
			UrlShortenerRpcGrpc.UrlShortenerRpcBlockingStub authenticatedStub = createAuthenticatedStub();

			// 3개 링크 생성
			CreateLinkResponse link1 = authenticatedStub.createLink(
				CreateLinkRequest.newBuilder().setRedirectUrl("https://example.com/1").build()
			);
			CreateLinkResponse link2 = authenticatedStub.createLink(
				CreateLinkRequest.newBuilder().setRedirectUrl("https://example.com/2").build()
			);
			CreateLinkResponse link3 = authenticatedStub.createLink(
				CreateLinkRequest.newBuilder().setRedirectUrl("https://example.com/3").build()
			);

			assertEquals(3, shortUrlJpaRepository.count());

			// link2만 삭제
			authenticatedStub.deleteLink(
				DeleteLinkRequest.newBuilder().setShortCode(link2.getShortCode()).build()
			);

			// then
			assertEquals(2, shortUrlJpaRepository.count());
			assertTrue(shortUrlJpaRepository.findByShortCode(link1.getShortCode()).isPresent());
			assertFalse(shortUrlJpaRepository.findByShortCode(link2.getShortCode()).isPresent());
			assertTrue(shortUrlJpaRepository.findByShortCode(link3.getShortCode()).isPresent());
		}
	}
}
