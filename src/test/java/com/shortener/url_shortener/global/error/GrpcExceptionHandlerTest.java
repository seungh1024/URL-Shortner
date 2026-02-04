package com.shortener.url_shortener.global.error;

import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * GrpcExceptionHandler 단위 테스트
 * 
 * 테스트 내용:
 * - CustomException → gRPC Status 변환
 * - HttpStatus별 매핑 검증
 * - 예상치 못한 에러 처리
 */
@DisplayName("GrpcExceptionHandler 단위 테스트")
class GrpcExceptionHandlerTest {

	private GrpcExceptionHandler handler;

	@BeforeEach
	void setUp() {
		handler = new GrpcExceptionHandler();
	}

	@Nested
	@DisplayName("CustomException 변환 테스트")
	class ConvertCustomExceptionTest {

		@Test
		@DisplayName("BAD_REQUEST → INVALID_ARGUMENT 변환")
		void badRequest_to_invalidArgument() {
			// given
			CustomException exception = new CustomException(
				HttpStatus.BAD_REQUEST,
				"Invalid parameter"
			);

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
			assertEquals("Invalid parameter", status.getDescription());
		}

		@Test
		@DisplayName("NOT_FOUND → NOT_FOUND 변환")
		void notFound_to_notFound() {
			// given
			CustomException exception = new CustomException(
				HttpStatus.NOT_FOUND,
				"Key not found"
			);

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.NOT_FOUND, status.getCode());
			assertEquals("Key not found", status.getDescription());
		}

		@Test
		@DisplayName("INTERNAL_SERVER_ERROR → INTERNAL 변환")
		void internalError_to_internal() {
			// given
			CustomException exception = new CustomException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Internal error"
			);

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.INTERNAL, status.getCode());
			assertEquals("Internal error", status.getDescription());
		}

		@Test
		@DisplayName("URL 생성 실패 메시지 → RESOURCE_EXHAUSTED 변환")
		void urlGenerationFailed_to_resourceExhausted() {
			// given
			CustomException exception = new CustomException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"URL 생성에 실패했습니다."
			);

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.getCode());
			assertEquals("URL 생성에 실패했습니다.", status.getDescription());
		}

		@Test
		@DisplayName("요청 취소 메시지 → CANCELLED 변환")
		void requestCancelled_to_cancelled() {
			// given
			CustomException exception = new CustomException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Request was cancelled by client"
			);

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.CANCELLED, status.getCode());
			assertEquals("Request was cancelled by client", status.getDescription());
		}

		@Test
		@DisplayName("CONFLICT → ALREADY_EXISTS 변환")
		void conflict_to_alreadyExists() {
			// given
			CustomException exception = new CustomException(
				HttpStatus.CONFLICT,
				"Resource already exists"
			);

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.ALREADY_EXISTS, status.getCode());
			assertEquals("Resource already exists", status.getDescription());
		}

		@Test
		@DisplayName("UNAUTHORIZED → PERMISSION_DENIED 변환")
		void unauthorized_to_permissionDenied() {
			// given
			CustomException exception = new CustomException(
				HttpStatus.UNAUTHORIZED,
				"Unauthorized access"
			);

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.PERMISSION_DENIED, status.getCode());
			assertEquals("Unauthorized access", status.getDescription());
		}

		@Test
		@DisplayName("TOO_MANY_REQUESTS → RESOURCE_EXHAUSTED 변환")
		void tooManyRequests_to_resourceExhausted() {
			// given
			CustomException exception = new CustomException(
				HttpStatus.TOO_MANY_REQUESTS,
				"Rate limit exceeded"
			);

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.getCode());
			assertEquals("Rate limit exceeded", status.getDescription());
		}

		@Test
		@DisplayName("SERVICE_UNAVAILABLE → UNAVAILABLE 변환")
		void serviceUnavailable_to_unavailable() {
			// given
			CustomException exception = new CustomException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Service temporarily unavailable"
			);

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.UNAVAILABLE, status.getCode());
			assertEquals("Service temporarily unavailable", status.getDescription());
		}
	}

	@Nested
	@DisplayName("일반 Exception 처리 테스트")
	class HandleGeneralExceptionTest {

		@Test
		@DisplayName("NullPointerException → INTERNAL 변환")
		void nullPointerException_to_internal() {
			// given
			Exception exception = new NullPointerException("Null value encountered");

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.INTERNAL, status.getCode());
			assertEquals("Internal server error", status.getDescription());
			assertNotNull(status.getCause());
		}

		@Test
		@DisplayName("RuntimeException → INTERNAL 변환")
		void runtimeException_to_internal() {
			// given
			Exception exception = new RuntimeException("Unexpected error");

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.INTERNAL, status.getCode());
			assertEquals("Internal server error", status.getDescription());
		}

		@Test
		@DisplayName("메시지 없는 Exception → INTERNAL 변환")
		void exceptionWithoutMessage_to_internal() {
			// given
			Exception exception = new RuntimeException();

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.INTERNAL, status.getCode());
			assertEquals("Internal server error", status.getDescription());
		}
	}
}
