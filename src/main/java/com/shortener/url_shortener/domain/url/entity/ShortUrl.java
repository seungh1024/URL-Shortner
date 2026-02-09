package com.shortener.url_shortener.domain.url.entity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
	@Index(name = "idx_hash_key", columnList = "hash_key"),
	@Index(name = "idx_short_code", columnList = "short_code", unique = true),
	@Index(name = "idx_expired_at_id", columnList = "expired_at, id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShortUrl {
	@Id
	private Long id;

	@Column(name = "hash_key", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] hashKey;

	@Column(name = "short_code", nullable = false, length = 8, unique = true)
	private String shortCode;

	@Column(name = "redirection_url", nullable = false, columnDefinition = "TEXT")
	private String redirectionUrl;

	@Column(name = "expired_at",nullable = false)
	private LocalDateTime expiredAt;

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

	public ShortUrl(Long id, byte[] hashKey, String shortCode, String redirectionUrl, LocalDateTime expiredAt) {
		this.id = id;
		this.hashKey = hashKey;
		this.shortCode = shortCode;
		this.redirectionUrl = redirectionUrl;
		this.expiredAt = expiredAt;
	}

	public ShortUrl(Long id, String shortCode, String redirectionUrl, LocalDateTime expiredAt) {
		this(id, sha256(redirectionUrl), shortCode, redirectionUrl, expiredAt);
	}

	private static byte[] sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return digest.digest(input.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

	public boolean isExpired() {
		return LocalDateTime.now().isAfter(this.expiredAt);
	}
}
