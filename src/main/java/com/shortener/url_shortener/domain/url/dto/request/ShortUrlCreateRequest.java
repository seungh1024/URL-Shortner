package com.shortener.url_shortener.domain.url.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShortUrlCreateRequest(
	@NotBlank
	@Size(max = 2048)
	String redirectUrl
) {
}
