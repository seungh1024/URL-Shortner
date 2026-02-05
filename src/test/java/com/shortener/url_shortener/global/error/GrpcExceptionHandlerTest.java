package com.shortener.url_shortener.global.error;

import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * GrpcExceptionHandler 단위 테스트
 * 
 * 테스트 내용:
 * - CustomException → gRPC Status 변환
 * - ErrorCode별 매핑 검증
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
		@DisplayName("INVALID_KEY_ERROR → INVALID_ARGUMENT 변환")
		void invalidKey_to_invalidArgument() {
			// given
			CustomException exception = ErrorCode.INVALID_KEY_ERROR.baseException("Invalid parameter");

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
			assertEquals(ErrorCode.INVALID_KEY_ERROR.getMessage(), status.getDescription());
		}

		@Test
		@DisplayName("KEY_NOT_FOUND → NOT_FOUND 변환")
		void keyNotFound_to_notFound() {
			// given
			CustomException exception = ErrorCode.KEY_NOT_FOUND.baseException("Key not found");

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.NOT_FOUND, status.getCode());
			assertEquals(ErrorCode.KEY_NOT_FOUND.getMessage(), status.getDescription());
		}

		@Test
		@DisplayName("EXPIRED_LINK → NOT_FOUND 변환")
		void expiredLink_to_notFound() {
			// given
			CustomException exception = ErrorCode.EXPIRED_LINK.baseException("Link expired");

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.NOT_FOUND, status.getCode());
			assertEquals(ErrorCode.EXPIRED_LINK.getMessage(), status.getDescription());
		}

		@Test
		@DisplayName("URL_GENERATION_FAILED → RESOURCE_EXHAUSTED 변환 (ErrorCode 기반)")
		void urlGenerationFailed_to_resourceExhausted() {
			// given
			CustomException exception = ErrorCode.URL_GENERATION_FAILED.baseException("Hash collision");

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.getCode());
			assertEquals(ErrorCode.URL_GENERATION_FAILED.getMessage(), status.getDescription());
		}

		@Test
		@DisplayName("REQUEST_CANCELLED → CANCELLED 변환 (ErrorCode 기반)")
		void requestCancelled_to_cancelled() {
			// given
			CustomException exception = ErrorCode.REQUEST_CANCELLED.baseException("Client cancelled");

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.CANCELLED, status.getCode());
			assertEquals(ErrorCode.REQUEST_CANCELLED.getMessage(), status.getDescription());
		}

		@Test
		@DisplayName("HASHING_FAILED → INTERNAL 변환")
		void hashingFailed_to_internal() {
			// given
			CustomException exception = ErrorCode.HASHING_FAILED.baseException("SHA-256 failed");

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.INTERNAL, status.getCode());
			assertEquals(ErrorCode.HASHING_FAILED.getMessage(), status.getDescription());
		}

		@Test
		@DisplayName("INVALID_ARGUMENT_ERROR → INVALID_ARGUMENT 변환")
		void invalidArgument_to_invalidArgument() {
			// given
			CustomException exception = ErrorCode.INVALID_ARGUMENT_ERROR.baseException("Bad request");

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
			assertEquals(ErrorCode.INVALID_ARGUMENT_ERROR.getMessage(), status.getDescription());
		}

		@Test
		@DisplayName("MISSING_REQUIRED_PARAMETER → INVALID_ARGUMENT 변환")
		void missingParameter_to_invalidArgument() {
			// given
			CustomException exception = ErrorCode.MISSING_REQUIRED_PARAMETER.baseException("Missing param");

			// when
			Status status = handler.convertToStatus(exception);

			// then
			assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
			assertEquals(ErrorCode.MISSING_REQUIRED_PARAMETER.getMessage(), status.getDescription());
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

	@Nested
	@DisplayName("ErrorCode 특수 케이스 우선순위 테스트")
	class ErrorCodePriorityTest {

		@Test
		@DisplayName("URL_GENERATION_FAILED는 HttpStatus가 INTERNAL이지만 RESOURCE_EXHAUSTED로 매핑")
		void urlGenerationFailed_overridesHttpStatus() {
			// given
			CustomException exception = ErrorCode.URL_GENERATION_FAILED.baseException();

			// when
			Status status = handler.convertToStatus(exception);

			// then
			// HttpStatus.INTERNAL_SERVER_ERROR → Status.INTERNAL이 아니라
			// ErrorCode 기반으로 Status.RESOURCE_EXHAUSTED로 변환되어야 함
			assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.getCode());
		}

		@Test
		@DisplayName("REQUEST_CANCELLED는 HttpStatus가 INTERNAL이지만 CANCELLED로 매핑")
		void requestCancelled_overridesHttpStatus() {
			// given
			CustomException exception = ErrorCode.REQUEST_CANCELLED.baseException();

			// when
			Status status = handler.convertToStatus(exception);

			// then
			// HttpStatus.INTERNAL_SERVER_ERROR → Status.INTERNAL이 아니라
			// ErrorCode 기반으로 Status.CANCELLED로 변환되어야 함
			assertEquals(Status.Code.CANCELLED, status.getCode());
		}
	}
}
