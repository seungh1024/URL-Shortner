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
		Long connectionId = currentConnectionId();
		log.debug("Acquire lock request. lockName={}, connectionId={}", lockName, connectionId);
		Integer result = jdbcTemplate.queryForObject(
			"SELECT GET_LOCK(?, ?)",
			Integer.class,
			lockName,
			timeoutSeconds
		);
		log.debug("Acquire lock result. lockName={}, connectionId={}, result={}", lockName, connectionId, result);
		return result != null && result == 1;
	}

	public void releaseLock(String lockName) {
		try {
			Long connectionId = currentConnectionId();
			log.debug("Release lock request. lockName={}, connectionId={}", lockName, connectionId);
			jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName);
		} catch (Exception e) {
			log.warn("Failed to release lock. lockName: {}", lockName, e);
		}
	}

	private Long currentConnectionId() {
		try {
			return jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Long.class);
		} catch (Exception e) {
			log.debug("Failed to fetch connection id for lock diagnostics", e);
			return null;
		}
	}
}
