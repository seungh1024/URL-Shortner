package com.shortener.url_shortener.domain.url.dto.response;

public record ShortUrlCreateResponse(
	String shortCode,
	String url
) {
}
