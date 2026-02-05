package com.shortener.url_shortener.global.error;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

	// 400
	INVALID_ARGUMENT_ERROR(HttpStatus.BAD_REQUEST, "올바르지 않은 파라미터입니다."),
	INVALID_KEY_ERROR(HttpStatus.BAD_REQUEST, "올바르지 않은 파라미터입니다."),
	MISSING_REQUIRED_PARAMETER(HttpStatus.BAD_REQUEST, "필수 파라미터가 누락되었습니다."),
	API_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로의 API를 찾을 수 없습니다."),
	KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 key의 URL이 존재하지 않습니다."),
	EXPIRED_LINK(HttpStatus.NOT_FOUND, "링크가 만료되었습니다."),
	// 500,
	URL_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "URL 생성에 실패했습니다."),
	REQUEST_CANCELLED(HttpStatus.INTERNAL_SERVER_ERROR, "Client connection cancelled"),
	HASHING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "해시 생성에 실패했습니다.");

	private final HttpStatus status;
	private final String message;

	public CustomException baseException() {
		return new CustomException(this);
	}

	public CustomException baseException(String debugMessage, Object... args) {
		return new CustomException(this, String.format(debugMessage, args));
	}
}
