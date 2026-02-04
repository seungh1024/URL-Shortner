package com.shortener.url_shortener.global.error;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

/**
 * CustomException을 gRPC Status로 변환
 *
 * gRPC Status Code 매핑:
 * - INVALID_ARGUMENT: 잘못된 파라미터
 * - NOT_FOUND: 리소스 없음
 * - RESOURCE_EXHAUSTED: 리소스 고갈 (재시도 가능)
 * - CANCELLED: 요청 취소
 * - INTERNAL: 내부 오류
 */
@Slf4j
@Component
public class GrpcExceptionHandler {

	/**
	 * Exception을 gRPC Status로 변환
	 *
	 * @param e 발생한 예외
	 * @return gRPC Status (클라이언트에게 전송될)
	 */
	public Status convertToStatus(Exception e) {

		if (e instanceof CustomException customException) {
			return mapCustomException(customException);
		}

		// 예상치 못한 에러 (NullPointerException 등)
		log.error("[gRPC] Unexpected error", e);
		return Status.INTERNAL
			.withDescription("Internal server error")
			.withCause(e);
	}

	/**
	 * CustomException을 gRPC Status로 매핑
	 *
	 * 매핑 전략:
	 * 1. HttpStatus 기반 매핑 (기본)
	 * 2. 메시지 패턴 인식 (특수 케이스)
	 */
	private Status mapCustomException(CustomException e) {
		HttpStatus httpStatus = e.getHttpStatus();
		String message = e.getMessage();

		// 특수 케이스: URL 생성 실패 (재시도 가능)
		if (message != null && message.contains("URL 생성")) {
			return Status.RESOURCE_EXHAUSTED
				.withDescription(message);
		}

		// 특수 케이스: 요청 취소
		if (message != null && message.contains("cancelled")) {
			return Status.CANCELLED
				.withDescription(message);
		}

		// HttpStatus 기반 매핑
		return switch (httpStatus) {
			case BAD_REQUEST -> Status.INVALID_ARGUMENT.withDescription(message);

			case NOT_FOUND -> Status.NOT_FOUND.withDescription(message);

			case CONFLICT -> Status.ALREADY_EXISTS.withDescription(message);

			case UNAUTHORIZED, FORBIDDEN -> Status.PERMISSION_DENIED.withDescription(message);

			case TOO_MANY_REQUESTS -> Status.RESOURCE_EXHAUSTED.withDescription(message);

			case SERVICE_UNAVAILABLE -> Status.UNAVAILABLE.withDescription(message);

			default -> Status.INTERNAL.withDescription(message != null ? message : "Internal error");
		};
	}
}