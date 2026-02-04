package com.shortener.url_shortener.domain.url.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shortener.url_shortener.domain.url.dto.response.LinkCreateResponse;
import com.shortener.url_shortener.domain.url.entity.URLShortener;
import com.shortener.url_shortener.domain.url.repository.URLShortenerJpaRepository;
import com.shortener.url_shortener.global.util.Base62Encoder;
import com.shortener.url_shortener.global.util.HashGenerator;
import com.shortener.url_shortener.global.util.TsidGenerator;
import com.shortener.url_shortener.global.error.ErrorCode;
import com.shortener.url_shortener.global.util.ShortenerStringUtil;

import io.grpc.Context;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class URLShortenerService {

	private final TsidGenerator tsidGenerator;
	private final URLShortenerJpaRepository urlShortenerJpaRepository;
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

	@Transactional
	public String getLink(String key) {
		validateHashKey(key);
		URLShortener urlShortener = urlShortenerJpaRepository.findByHashKey(key)
			.orElseThrow(() -> ErrorCode.KEY_NOT_FOUND.baseException(
				ShortenerStringUtil.format("Get link failed. key: {}", key)
			));

		if (urlShortener.isExpired()) {
			throw ErrorCode.EXPIRED_LINK.baseException(
				ShortenerStringUtil.format("Link expired. key: {}", key)
			);
		}

		return urlShortener.getRedirectionUrl();
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
				triedOffset |= (1L << offset);
				offset = new SecureRandom().nextInt(encodedHash.length() - hashKeySize);
			} while ((triedOffset & (1L << offset)) == 0);
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
		urlShortenerJpaRepository.deleteByHashKey(key);
	}

	private boolean trySaveHashKey(Long id, String hashKey, String redirectURL) {
		try {
			URLShortener urlShortener = new URLShortener(id, hashKey, redirectURL,
				LocalDateTime.now().plusDays(defaultExpirationDays));

			urlShortenerJpaRepository.save(urlShortener);
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

}
