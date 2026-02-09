package com.shortener.url_shortener.domain.url.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShortUrlLockRepository 단위 테스트")
class ShortUrlLockRepositoryTest {

	@Mock
	private JdbcTemplate jdbcTemplate;

	@InjectMocks
	private ShortUrlLockRepository shortUrlLockRepository;

	@Test
	@DisplayName("락 획득 성공 시 true 반환")
	void acquireLock_success() {
		when(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class), any(), any()))
			.thenReturn(1);

		boolean result = shortUrlLockRepository.acquireLock("lock", 3);

		assertTrue(result);
	}

	@Test
	@DisplayName("락 획득 실패 시 false 반환")
	void acquireLock_fail() {
		when(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class), any(), any()))
			.thenReturn(0);

		boolean result = shortUrlLockRepository.acquireLock("lock", 3);

		assertFalse(result);
	}

	@Test
	@DisplayName("락 해제 실패 시 예외를 던지지 않음")
	void releaseLock_failure_doesNotThrow() {
		doThrow(new RuntimeException("release failed"))
			.when(jdbcTemplate)
			.queryForObject(eq("SELECT RELEASE_LOCK(?)"), eq(Integer.class), anyString());

		assertDoesNotThrow(() -> shortUrlLockRepository.releaseLock("lock"));
	}
}
