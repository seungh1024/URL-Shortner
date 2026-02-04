package com.shortener.url_shortener.global.interceptor;

import io.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ApiKeyInterceptor 단위 테스트
 * 
 * 테스트 내용:
 * - API Key 검증 로직
 * - 성공/실패 케이스
 * - 에러 메시지 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyInterceptor 단위 테스트")
class ApiKeyInterceptorTest {

	private static final String VALID_API_KEY = "test-api-key-12345";
	private static final String API_KEY_HEADER = "x-api-key";
	private static final Metadata.Key<String> API_KEY_METADATA_KEY =
		Metadata.Key.of(API_KEY_HEADER, Metadata.ASCII_STRING_MARSHALLER);

	private ApiKeyInterceptor interceptor;

	@Mock
	private ServerCall<String, String> serverCall;

	@Mock
	private ServerCallHandler<String, String> next;

	@Mock
	private MethodDescriptor<String, String> methodDescriptor;

	@Captor
	private ArgumentCaptor<Status> statusCaptor;

	@Captor
	private ArgumentCaptor<Metadata> metadataCaptor;

	@BeforeEach
	void setUp() {
		interceptor = new ApiKeyInterceptor();
		ReflectionTestUtils.setField(interceptor, "validApiKey", VALID_API_KEY);

		// Mock 설정
		when(serverCall.getMethodDescriptor()).thenReturn(methodDescriptor);
		when(methodDescriptor.getFullMethodName()).thenReturn("url.UrlShortenerRpc/CreateLink");
	}

	@Nested
	@DisplayName("API Key 검증 성공 테스트")
	class SuccessTests {

		@Test
		@DisplayName("올바른 API Key로 요청 시 통과")
		void validApiKey_shouldPass() {
			// given
			Metadata headers = new Metadata();
			headers.put(API_KEY_METADATA_KEY, VALID_API_KEY);

			ServerCall.Listener<String> mockListener = mock(ServerCall.Listener.class);
			when(next.startCall(any(), any())).thenReturn(mockListener);

			// when
			ServerCall.Listener<String> result = interceptor.interceptCall(serverCall, headers, next);

			// then
			verify(next, times(1)).startCall(serverCall, headers);
			verify(serverCall, never()).close(any(), any());
			assertNotNull(result);
			assertEquals(mockListener, result);
		}
	}

	@Nested
	@DisplayName("API Key 검증 실패 테스트")
	class FailureTests {

		@Test
		@DisplayName("API Key가 없으면 UNAUTHENTICATED 에러")
		void missingApiKey_shouldReturnUnauthenticated() {
			// given
			Metadata headers = new Metadata();  // API Key 없음

			// when
			ServerCall.Listener<String> result = interceptor.interceptCall(serverCall, headers, next);

			// then
			verify(serverCall, times(1)).close(statusCaptor.capture(), metadataCaptor.capture());
			verify(next, never()).startCall(any(), any());

			Status status = statusCaptor.getValue();
			assertEquals(Status.Code.UNAUTHENTICATED, status.getCode());
			assertEquals("API Key is required", status.getDescription());

			assertNotNull(result);  // 빈 Listener 반환
		}

		@Test
		@DisplayName("빈 API Key로 요청 시 UNAUTHENTICATED 에러")
		void emptyApiKey_shouldReturnUnauthenticated() {
			// given
			Metadata headers = new Metadata();
			headers.put(API_KEY_METADATA_KEY, "");  // 빈 문자열

			// when
			ServerCall.Listener<String> result = interceptor.interceptCall(serverCall, headers, next);

			// then
			verify(serverCall, times(1)).close(statusCaptor.capture(), metadataCaptor.capture());
			verify(next, never()).startCall(any(), any());

			Status status = statusCaptor.getValue();
			assertEquals(Status.Code.UNAUTHENTICATED, status.getCode());
			assertEquals("API Key is required", status.getDescription());
		}

		@Test
		@DisplayName("잘못된 API Key로 요청 시 UNAUTHENTICATED 에러")
		void invalidApiKey_shouldReturnUnauthenticated() {
			// given
			Metadata headers = new Metadata();
			headers.put(API_KEY_METADATA_KEY, "wrong-api-key");

			// when
			ServerCall.Listener<String> result = interceptor.interceptCall(serverCall, headers, next);

			// then
			verify(serverCall, times(1)).close(statusCaptor.capture(), metadataCaptor.capture());
			verify(next, never()).startCall(any(), any());

			Status status = statusCaptor.getValue();
			assertEquals(Status.Code.UNAUTHENTICATED, status.getCode());
			assertEquals("Invalid API Key", status.getDescription());
		}

		@Test
		@DisplayName("대소문자가 다른 API Key는 실패")
		void caseIncorrectApiKey_shouldFail() {
			// given
			Metadata headers = new Metadata();
			headers.put(API_KEY_METADATA_KEY, VALID_API_KEY.toUpperCase());

			// when
			ServerCall.Listener<String> result = interceptor.interceptCall(serverCall, headers, next);

			// then
			verify(serverCall, times(1)).close(statusCaptor.capture(), metadataCaptor.capture());
			verify(next, never()).startCall(any(), any());

			Status status = statusCaptor.getValue();
			assertEquals(Status.Code.UNAUTHENTICATED, status.getCode());
		}
	}

	@Nested
	@DisplayName("메서드별 검증 테스트")
	class MethodSpecificTests {

		@Test
		@DisplayName("CreateLink 메서드도 API Key 검증")
		void createLink_requiresApiKey() {
			// given
			when(methodDescriptor.getFullMethodName()).thenReturn("url.UrlShortenerRpc/CreateLink");
			Metadata headers = new Metadata();

			// when
			interceptor.interceptCall(serverCall, headers, next);

			// then
			verify(serverCall, times(1)).close(any(Status.class), any(Metadata.class));
		}

		@Test
		@DisplayName("DeleteLink 메서드도 API Key 검증")
		void deleteLink_requiresApiKey() {
			// given
			when(methodDescriptor.getFullMethodName()).thenReturn("url.UrlShortenerRpc/DeleteLink");
			Metadata headers = new Metadata();

			// when
			interceptor.interceptCall(serverCall, headers, next);

			// then
			verify(serverCall, times(1)).close(any(Status.class), any(Metadata.class));
		}
	}
}
