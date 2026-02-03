package com.shortener.url_shortener.domain.url.dto;

public record LinkCreateResponse(
	String hashKey,
	String url
) {
}
