package com.shortener.url_shortener.domain.url.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ShortUrlLockRepository {

	private final JdbcTemplate jdbcTemplate;

	public boolean acquireLock(String lockName, int timeoutSeconds) {
		Integer result = jdbcTemplate.queryForObject(
			"SELECT GET_LOCK(?, ?)",
			Integer.class,
			lockName,
			timeoutSeconds
		);
		return result != null && result == 1;
	}

	public void releaseLock(String lockName) {
		try {
			jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName);
		} catch (Exception e) {
			log.warn("Failed to release lock. lockName: {}", lockName, e);
		}
	}
}
