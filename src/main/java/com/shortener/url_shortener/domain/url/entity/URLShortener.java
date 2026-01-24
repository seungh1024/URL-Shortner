package com.shortener.url_shortener.domain.url.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

@Entity
@Getter
public class URLShortener {
	@Id
	private Long id;

	String key;

	String redirectionLink;

	LocalDateTime TTL;
}
