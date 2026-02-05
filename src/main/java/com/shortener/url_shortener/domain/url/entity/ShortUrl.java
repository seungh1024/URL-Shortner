package com.shortener.url_shortener.domain.url.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "url_shortener", indexes = {
	@Index(name = "idx_hash_key", columnList = "hash_key", unique = true),
	@Index(name = "idx_expired_at_id", columnList = "expired_at, id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShortUrl {
	@Id
	private Long id;

	@Column(name = "hash_key", nullable = false, length = 8, unique = true)
	String hashKey;

	@Column(name = "redirection_url", nullable = false, columnDefinition = "TEXT")
	String redirectionUrl;

	@Column(name = "expired_at",nullable = false)
	LocalDateTime expiredAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}

	public ShortUrl(Long id, String hashKey, String redirectionUrl, LocalDateTime expiredAt) {
		this.id = id;
		this.hashKey = hashKey;
		this.redirectionUrl = redirectionUrl;
		this.expiredAt = expiredAt;
	}

	public void updateRedirectUrl(String redirectURL) {
		this.redirectionUrl = redirectURL;
	}

	public boolean isExpired() {
		return LocalDateTime.now().isAfter(this.expiredAt);
	}
}
