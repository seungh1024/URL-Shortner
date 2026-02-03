package com.shortener.url_shortener.domain.url.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shortener.url_shortener.domain.url.entity.URLShortener;

@Repository
public interface URLShortenerJpaRepository extends JpaRepository<URLShortener, Long> {

	Optional<URLShortener> findByHashKey(String key);

	int deleteByHashKey(String hashKey);
}
