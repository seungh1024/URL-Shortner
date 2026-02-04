package com.shortener.url_shortener.domain.url.dto.response;

public record LinkCreateResponse(
	String hashKey,
	String url
) {
}
