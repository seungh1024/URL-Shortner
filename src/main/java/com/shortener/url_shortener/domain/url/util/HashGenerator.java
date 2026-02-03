package com.shortener.url_shortener.domain.url.util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Component
public class HashGenerator {

	private static final String ALGORITHM = "SHA-256";

	/**
	 * 문자열을 SHA-256으로 해싱
	 *
	 * @param input 해싱할 문자열
	 * @return 해시 결과 (32 bytes)
	 */
	public byte[] hash(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
			return digest.digest(input.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			log.error("SHA-256 algorithm not available", e);
			throw new RuntimeException("Failed to generate hash", e);
		}
	}

	/**
	 * TSID + redirectUrl을 해싱
	 *
	 * @param tsid TSID
	 * @param redirectUrl 리다이렉트 URL
	 * @return 해시 결과 (32 bytes)
	 */
	public byte[] hash(Long tsid, String redirectUrl) {
		String input = tsid + redirectUrl;
		return hash(input);
	}

	/**
	 * TSID + redirectUrl + counter를 해싱 (충돌 시 재시도용)
	 *
	 * @param tsid TSID
	 * @param redirectUrl 리다이렉트 URL
	 * @param counter 재시도 카운터
	 * @return 해시 결과 (32 bytes)
	 */
	public byte[] hash(Long tsid, String redirectUrl, int counter) {
		String input = tsid + redirectUrl + counter;
		return hash(input);
	}

	/**
	 * 해시 결과를 16진수 문자열로 변환 (디버깅용)
	 *
	 * @param hash 해시 바이트 배열
	 * @return 16진수 문자열 (64자리)
	 */
	public String toHexString(byte[] hash) {
		StringBuilder hexString = new StringBuilder();
		for (byte b : hash) {
			String hex = Integer.toHexString(0xff & b);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}
}