package com.shortener.url_shortener.domain.url.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.shortener.url_shortener.domain.url.dto.ExpiredUrlView;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ShortUrlRepository {

	private final ShortUrlJpaRepository shortUrlJpaRepository;

	public List<ExpiredUrlView> selectShortUrlsWithPagination(Long id, LocalDateTime maxExpirationTime,
		LocalDateTime lastExpirationTime, int size) {
		if (id == null) {
			return shortUrlJpaRepository.findExpiredUrlIdsFirstPage(maxExpirationTime, size);
		}

		return shortUrlJpaRepository.findExpiredUrlIdsAfter(id, maxExpirationTime, lastExpirationTime, size);
	}
}
