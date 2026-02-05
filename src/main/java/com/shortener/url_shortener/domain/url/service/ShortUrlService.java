package com.shortener.url_shortener.domain.url.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shortener.url_shortener.domain.url.dto.response.LinkCreateResponse;
import com.shortener.url_shortener.domain.url.entity.ShortUrl;
import com.shortener.url_shortener.domain.url.repository.ShortUrlJpaRepository;
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

	private static final SecureRandom secureRandom = new SecureRandom();

	@Transactional
	public String getLink(String key) {
		validateHashKey(key);
		ShortUrl shortUrl = shortUrlJpaRepository.findByHashKey(key)
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
	public LinkCreateResponse createLink(String redirectURL) {
		Long id = tsidGenerator.nextKey();
		byte[] hash = hashGenerator.hash(id, redirectURL); // 동일 URL에서의 재발급을 위해 id를 더해줌
		String encodedHash = base62Encoder.encode(hash);

		int offset = encodedHash.length();
		long triedOffset = 0;

		for (int i = 0; i < retry; i++) {
			// DB 저장 전 취소 확인(timeout 등)
			if (Context.current().isCancelled()) {
				log.warn("Request cancelled, stopping processing");
				throw ErrorCode.REQUEST_CANCELLED.baseException(
					"Request was cancelled by client"
				);
			}
			do {
				offset = secureRandom.nextInt(encodedHash.length() - hashKeySize);
			} while (isOffsetAlreadyTried(triedOffset, offset));  // 명확한 의미
			triedOffset |= (1L << offset);

			String hashKey = encodedHash.substring(offset, offset + hashKeySize);
			if (trySaveHashKey(id, hashKey, redirectURL)) {
				return new LinkCreateResponse(hashKey, toShortUrl(hashKey));
			}
		}

		throw ErrorCode.URL_GENERATION_FAILED.baseException(
			ShortenerStringUtil.format("Failed to generate URL. hash conflicted. redirectURL: {}", redirectURL)
		);
	}

	@Transactional
	public void deleteLink(String key) {
		validateHashKey(key);
		shortUrlJpaRepository.deleteByHashKey(key);
	}

	private boolean trySaveHashKey(Long id, String hashKey, String redirectURL) {
		try {
			ShortUrl shortUrl = new ShortUrl(id, hashKey, redirectURL,
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

	private void validateHashKey(String key) {
		if (!base62Encoder.isValid(key)) {
			throw ErrorCode.INVALID_KEY_ERROR.baseException(
				ShortenerStringUtil.format("Invalid parameter from getLink. key: {}", key)
			);
		}
	}

	private boolean isOffsetAlreadyTried(long triedOffset, int offset) {
		return (triedOffset & (1L << offset)) != 0;
	}

}
