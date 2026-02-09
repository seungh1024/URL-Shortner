package com.shortener.url_shortener.domain.url.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shortener.url_shortener.domain.url.dto.response.ShortUrlCreateResponse;
import com.shortener.url_shortener.domain.url.entity.ShortUrl;
import com.shortener.url_shortener.domain.url.repository.ShortUrlJpaRepository;
import com.shortener.url_shortener.domain.url.repository.ShortUrlLockRepository;
import com.shortener.url_shortener.global.error.ErrorCode;
import com.shortener.url_shortener.global.util.Base62Encoder;
import com.shortener.url_shortener.global.util.HashGenerator;
import com.shortener.url_shortener.global.util.ShortenerStringUtil;
import com.shortener.url_shortener.global.util.TsidGenerator;

import io.grpc.Context;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortUrlService {

	private final TsidGenerator tsidGenerator;
	private final ShortUrlJpaRepository shortUrlJpaRepository;
	private final ShortUrlLockRepository shortUrlLockRepository;
	private final Base62Encoder base62Encoder;
	private final HashGenerator hashGenerator;

	@Value("${server.redirection.domain}")
	private String redirectionBaseDomain;

	@Value("${constant.default-expiration-days}")
	private int defaultExpirationDays;

	@Value("${constant.hash.length}")
	private int hashKeySize;

	@Value("${constant.hash.conflict.retry}")
	private int retry;

	@Value("${constant.url.max-length:2048}")
	private int maxUrlLength;

	@Value("${constant.hash.lock-timeout-seconds:3}")
	private int lockTimeoutSeconds;

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	@Transactional
	public String getLink(String key) {
		validateShortCode(key);
		ShortUrl shortUrl = shortUrlJpaRepository.findByShortCode(key)
			.orElseThrow(() -> ErrorCode.KEY_NOT_FOUND.baseException(
				ShortenerStringUtil.format("Get link failed. key: {}", key)
			));

		if (shortUrl.isExpired()) {
			throw ErrorCode.EXPIRED_LINK.baseException(
				ShortenerStringUtil.format("Link expired. key: {}", key)
			);
		}

		return shortUrl.getRedirectionUrl();
	}

	@Transactional
	public ShortUrlCreateResponse createLink(String redirectURL) {
		validateRedirectUrl(redirectURL);
		byte[] hashKey = hashGenerator.hash(redirectURL);
		String lockName = createLockName(hashKey);
		boolean locked = false;
		boolean releaseInFinally = true;

		try {
			locked = shortUrlLockRepository.acquireLock(lockName, lockTimeoutSeconds);
			if (!locked) {
				throw ErrorCode.URL_GENERATION_FAILED.baseException(
					ShortenerStringUtil.format("Failed to acquire lock. lockName: {}", lockName)
				);
			}
			if (TransactionSynchronizationManager.isSynchronizationActive()) {
				log.debug("Registering lock release afterCompletion. lockName={}", lockName);
				TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
					@Override
					public void afterCompletion(int status) {
						log.debug("afterCompletion release. lockName={}, status={}", lockName, status);
						shortUrlLockRepository.releaseLock(lockName);
					}
				});
				releaseInFinally = false;
			}
			LocalDateTime now = LocalDateTime.now();

			List<ShortUrl> existing = shortUrlJpaRepository.findByHashKeyAndExpiredAtAfter(hashKey, now);
			for (ShortUrl candidate : existing) {
				if (candidate.getRedirectionUrl().equals(redirectURL)) {
					return new ShortUrlCreateResponse(candidate.getShortCode(), toShortUrl(candidate.getShortCode()));
				}
			}

			Long id = tsidGenerator.nextKey();
			for (int i = 0; i < retry; i++) {
				// DB 저장 전 취소 확인(timeout 등)
				if (Context.current().isCancelled()) {
					log.warn("Request cancelled, stopping processing");
					throw ErrorCode.REQUEST_CANCELLED.baseException(
						"Request was cancelled by client"
					);
				}

				String shortCode = base62Encoder.random(hashKeySize, SECURE_RANDOM);
				if (trySaveShortCode(id, hashKey, shortCode, redirectURL)) {
					return new ShortUrlCreateResponse(shortCode, toShortUrl(shortCode));
				}
			}

			throw ErrorCode.URL_GENERATION_FAILED.baseException(
				ShortenerStringUtil.format("Failed to generate URL. short_code conflicted. redirectURL: {}", redirectURL)
			);
		} finally {
			if (locked && releaseInFinally) {
				shortUrlLockRepository.releaseLock(lockName);
			}
		}
	}

	@Transactional
	public void deleteLink(String key) {
		validateShortCode(key);
		shortUrlJpaRepository.deleteByShortCode(key);
	}

	private boolean trySaveShortCode(Long id, byte[] hashKey, String shortCode, String redirectURL) {
		try {
			ShortUrl shortUrl = new ShortUrl(id, hashKey, shortCode, redirectURL,
				LocalDateTime.now().plusDays(defaultExpirationDays));

			shortUrlJpaRepository.save(shortUrl);
			return true;

		} catch (DataIntegrityViolationException e) {
			// 충돌 발생
			return false;
		}
	}

	private String toShortUrl(String key) {
		StringBuilder sb = new StringBuilder(redirectionBaseDomain);
		if (!redirectionBaseDomain.endsWith("/")) {
			sb.append("/");
		}
		sb.append(key);

		return sb.toString();
	}

	private void validateShortCode(String key) {
		if (!base62Encoder.isValid(key)) {
			throw ErrorCode.INVALID_KEY_ERROR.baseException(
				ShortenerStringUtil.format("Invalid parameter from getLink. key: {}", key)
			);
		}
	}

	private void validateRedirectUrl(String redirectURL) {
		if (redirectURL == null || redirectURL.isBlank()) {
			throw ErrorCode.INVALID_ARGUMENT_ERROR.baseException(
				ShortenerStringUtil.format("Invalid redirect URL. url: {}", redirectURL)
			);
		}
		if (redirectURL.length() > maxUrlLength) {
			throw ErrorCode.INVALID_ARGUMENT_ERROR.baseException(
				ShortenerStringUtil.format("Redirect URL too long. length: {}", redirectURL.length())
			);
		}

		try {
			java.net.URI uri = new java.net.URI(redirectURL);
			String scheme = uri.getScheme();
			if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
				throw ErrorCode.INVALID_ARGUMENT_ERROR.baseException(
					ShortenerStringUtil.format("Invalid redirect URL scheme. url: {}", redirectURL)
				);
			}
			String host = uri.getHost();
			if (host == null || host.isBlank()) {
				throw ErrorCode.INVALID_ARGUMENT_ERROR.baseException(
					ShortenerStringUtil.format("Invalid redirect URL host. url: {}", redirectURL)
				);
			}
		} catch (java.net.URISyntaxException e) {
			throw ErrorCode.INVALID_ARGUMENT_ERROR.baseException(
				ShortenerStringUtil.format("Invalid redirect URL. url: {}", redirectURL)
			);
		}
	}

	private String createLockName(byte[] hashKey) {
		String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(hashKey);
		return "url:" + encoded;
	}

}
