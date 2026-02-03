package com.shortener.url_shortener.domain.url.dto.request;

import jakarta.validation.constraints.NotNull;

public record LinkRequestDto(
	@NotNull String redirectURL
) {
}
