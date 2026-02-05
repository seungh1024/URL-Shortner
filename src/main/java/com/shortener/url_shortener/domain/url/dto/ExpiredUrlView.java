package com.shortener.url_shortener.domain.url.dto;

import java.time.LocalDateTime;

public interface ExpiredUrlView {
	LocalDateTime getExpiredAt();

	Long getId();
}
