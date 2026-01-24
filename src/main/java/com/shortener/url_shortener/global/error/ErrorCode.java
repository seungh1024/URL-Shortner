package com.shortener.url_shortener.global.error;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

	// 400
	INVALID_ARGUMENT_ERROR(HttpStatus.BAD_REQUEST, "올바르지 않은 파라미터입니다."),
	MISSING_REQUIRED_PARAMETER(HttpStatus.BAD_REQUEST, "필수 파라미터가 누락되었습니다."),
	API_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로의 API를 찾을 수 없습니다."),
	// 500,
	;

	private final HttpStatus status;
	private final String message;

	public CustomException baseException() {
		return new CustomException(status, message);
	}

	public CustomException baseException(String debugMessage, Object... args) {
		return new CustomException(status, message, String.format(debugMessage, args));
	}
}
