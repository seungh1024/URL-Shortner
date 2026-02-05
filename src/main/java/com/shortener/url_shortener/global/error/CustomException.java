package com.shortener.url_shortener.global.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

	private final ErrorCode errorCode;
	private final HttpStatus httpStatus;
	private final String message;
	private final String debugMessage;

	public CustomException(ErrorCode errorCode) {
		super(createMessageForm(errorCode.getStatus(), errorCode.getMessage(), null));
		this.errorCode = errorCode;
		this.httpStatus = errorCode.getStatus();
		this.message = errorCode.getMessage();
		this.debugMessage = null;
	}

	public CustomException(ErrorCode errorCode, String debugMessage) {
		super(createMessageForm(errorCode.getStatus(), errorCode.getMessage(), debugMessage));
		this.errorCode = errorCode;
		this.httpStatus = errorCode.getStatus();
		this.message = errorCode.getMessage();
		this.debugMessage = debugMessage;
	}

	private static String createMessageForm(HttpStatus httpStatus, String message, String debugMessage) {
		StringBuilder detailMessage = new StringBuilder(httpStatus.toString()).append(": ").append(message);
		if (debugMessage != null && !debugMessage.isEmpty()) {
			detailMessage.append(", ").append(debugMessage);
		}
		return detailMessage.toString();
	}
}
