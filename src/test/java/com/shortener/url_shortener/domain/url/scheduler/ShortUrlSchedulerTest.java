package com.shortener.url_shortener.domain.url.scheduler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.shortener.url_shortener.domain.url.dto.ExpiredUrlView;
import com.shortener.url_shortener.domain.url.repository.ShortUrlJpaRepository;
import com.shortener.url_shortener.domain.url.repository.ShortUrlRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ShortUrlScheduler 단위 테스트
 *
 * 테스트 내용:
 * - 정상 배치 처리
 * - 조회 실패 시 스케줄러 중단
 * - 삭제 실패 시 다음 배치 계속 진행
 * - 빈 배치 처리
 * - 페이징 처리 (커서 업데이트)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShortUrlScheduler 단위 테스트")
class ShortUrlSchedulerTest {

	@Mock
	private ShortUrlRepository shortUrlRepository;

	@Mock
	private ShortUrlJpaRepository shortUrlJpaRepository;

	@InjectMocks
	private ShortUrlScheduler shortUrlScheduler;

	private final int batchSize = 10;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(shortUrlScheduler, "batchSize", batchSize);
	}

	@Nested
	@DisplayName("정상 배치 처리 테스트")
	class NormalBatchProcessingTest {

		@Test
		@DisplayName("성공: 단일 배치 (데이터가 배치 크기보다 적음)")
		void singleBatch_lessThanBatchSize() {
			// given
			List<ExpiredUrlView> firstBatch = createExpiredUrlViews(5, LocalDateTime.now().minusDays(1));

			when(shortUrlRepository.selectShortUrlsWithPagination(
				isNull(), any(LocalDateTime.class), isNull(), eq(batchSize)
			)).thenReturn(firstBatch);

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			verify(shortUrlRepository, times(1)).selectShortUrlsWithPagination(
				isNull(), any(LocalDateTime.class), isNull(), eq(batchSize)
			);
			verify(shortUrlJpaRepository, times(1)).deleteAllByIdInBatch(anyList());
		}

		@Test
		@DisplayName("성공: 다중 배치 (데이터가 배치 크기의 정확히 2배)")
		void multipleBatches_exactlyTwoBatches() {
			// given
			LocalDateTime baseTime = LocalDateTime.now().minusDays(1);
			List<ExpiredUrlView> firstBatch = createExpiredUrlViews(10, baseTime);
			List<ExpiredUrlView> secondBatch = createExpiredUrlViews(10, baseTime.plusHours(1));
			List<ExpiredUrlView> emptyBatch = List.of();

			when(shortUrlRepository.selectShortUrlsWithPagination(
				isNull(), any(LocalDateTime.class), isNull(), eq(batchSize)
			)).thenReturn(firstBatch);

			when(shortUrlRepository.selectShortUrlsWithPagination(
				eq(firstBatch.get(9).getId()), any(LocalDateTime.class), 
				eq(firstBatch.get(9).getExpiredAt()), eq(batchSize)
			)).thenReturn(secondBatch);

			when(shortUrlRepository.selectShortUrlsWithPagination(
				eq(secondBatch.get(9).getId()), any(LocalDateTime.class), 
				eq(secondBatch.get(9).getExpiredAt()), eq(batchSize)
			)).thenReturn(emptyBatch);

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			verify(shortUrlRepository, times(3)).selectShortUrlsWithPagination(
				any(), any(LocalDateTime.class), any(), eq(batchSize)
			);
			verify(shortUrlJpaRepository, times(2)).deleteAllByIdInBatch(anyList());
		}

		@Test
		@DisplayName("성공: 마지막 배치가 배치 크기보다 작음")
		void lastBatch_lessThanBatchSize() {
			// given
			LocalDateTime baseTime = LocalDateTime.now().minusDays(1);
			List<ExpiredUrlView> firstBatch = createExpiredUrlViews(10, baseTime);
			List<ExpiredUrlView> lastBatch = createExpiredUrlViews(5, baseTime.plusHours(1));

			when(shortUrlRepository.selectShortUrlsWithPagination(
				isNull(), any(LocalDateTime.class), isNull(), eq(batchSize)
			)).thenReturn(firstBatch);

			when(shortUrlRepository.selectShortUrlsWithPagination(
				eq(firstBatch.get(9).getId()), any(LocalDateTime.class), 
				eq(firstBatch.get(9).getExpiredAt()), eq(batchSize)
			)).thenReturn(lastBatch);

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			verify(shortUrlRepository, times(2)).selectShortUrlsWithPagination(
				any(), any(LocalDateTime.class), any(), eq(batchSize)
			);
			verify(shortUrlJpaRepository, times(2)).deleteAllByIdInBatch(anyList());
		}
	}

	@Nested
	@DisplayName("조회 실패 시나리오")
	class QueryFailureTest {

		@Test
		@DisplayName("조회 실패 시 스케줄러 중단")
		void queryFails_stopScheduler() {
			// given
			when(shortUrlRepository.selectShortUrlsWithPagination(
				isNull(), any(LocalDateTime.class), isNull(), eq(batchSize)
			)).thenThrow(new RuntimeException("Database connection failed"));

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			verify(shortUrlRepository, times(1)).selectShortUrlsWithPagination(
				any(), any(LocalDateTime.class), any(), eq(batchSize)
			);
			verify(shortUrlJpaRepository, never()).deleteAllByIdInBatch(anyList());
		}

		@Test
		@DisplayName("두 번째 배치 조회 실패 시 첫 번째 배치는 처리되고 중단")
		void secondBatchQueryFails_firstBatchProcessed() {
			// given
			List<ExpiredUrlView> firstBatch = createExpiredUrlViews(10, LocalDateTime.now().minusDays(1));

			when(shortUrlRepository.selectShortUrlsWithPagination(
				isNull(), any(LocalDateTime.class), isNull(), eq(batchSize)
			)).thenReturn(firstBatch);

			when(shortUrlRepository.selectShortUrlsWithPagination(
				eq(firstBatch.get(9).getId()), any(LocalDateTime.class), 
				eq(firstBatch.get(9).getExpiredAt()), eq(batchSize)
			)).thenThrow(new RuntimeException("Database connection failed"));

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			verify(shortUrlRepository, times(2)).selectShortUrlsWithPagination(
				any(), any(LocalDateTime.class), any(), eq(batchSize)
			);
			verify(shortUrlJpaRepository, times(1)).deleteAllByIdInBatch(anyList());
		}
	}

	@Nested
	@DisplayName("삭제 실패 시나리오")
	class DeleteFailureTest {

		@Test
		@DisplayName("삭제 실패 시 다음 배치 계속 진행")
		void deleteFails_continueToNextBatch() {
			// given
			LocalDateTime baseTime = LocalDateTime.now().minusDays(1);
			List<ExpiredUrlView> firstBatch = createExpiredUrlViews(10, baseTime);
			List<ExpiredUrlView> secondBatch = createExpiredUrlViews(10, baseTime.plusHours(1));
			List<ExpiredUrlView> emptyBatch = List.of();

			when(shortUrlRepository.selectShortUrlsWithPagination(
				isNull(), any(LocalDateTime.class), isNull(), eq(batchSize)
			)).thenReturn(firstBatch);

			when(shortUrlRepository.selectShortUrlsWithPagination(
				eq(firstBatch.get(9).getId()), any(LocalDateTime.class), 
				eq(firstBatch.get(9).getExpiredAt()), eq(batchSize)
			)).thenReturn(secondBatch);

			when(shortUrlRepository.selectShortUrlsWithPagination(
				eq(secondBatch.get(9).getId()), any(LocalDateTime.class), 
				eq(secondBatch.get(9).getExpiredAt()), eq(batchSize)
			)).thenReturn(emptyBatch);

			// 첫 번째 배치 삭제 실패
			doThrow(new RuntimeException("Delete failed"))
				.when(shortUrlJpaRepository).deleteAllByIdInBatch(
					argThat(list -> {
						if (!(list instanceof List)) return false;
						List<?> l = (List<?>) list;
						return l.size() == 10 && l.contains(1L);
					})
				);

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			verify(shortUrlRepository, times(3)).selectShortUrlsWithPagination(
				any(), any(LocalDateTime.class), any(), eq(batchSize)
			);
			verify(shortUrlJpaRepository, times(2)).deleteAllByIdInBatch(anyList());
		}
	}

	@Nested
	@DisplayName("빈 배치 처리")
	class EmptyBatchTest {

		@Test
		@DisplayName("첫 조회부터 빈 결과면 삭제 호출 안 함")
		void firstQueryReturnsEmpty_noDeleteCalled() {
			// given
			when(shortUrlRepository.selectShortUrlsWithPagination(
				isNull(), any(LocalDateTime.class), isNull(), eq(batchSize)
			)).thenReturn(List.of());

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			verify(shortUrlRepository, times(1)).selectShortUrlsWithPagination(
				any(), any(LocalDateTime.class), any(), eq(batchSize)
			);
			verify(shortUrlJpaRepository, never()).deleteAllByIdInBatch(anyList());
		}
	}

	@Nested
	@DisplayName("커서 업데이트 검증")
	class CursorUpdateTest {

		@Test
		@DisplayName("커서가 마지막 항목의 id와 expiredAt으로 업데이트됨")
		void cursorUpdated_withLastItem() {
			// given
			LocalDateTime baseTime = LocalDateTime.now().minusDays(1);
			List<ExpiredUrlView> firstBatch = createExpiredUrlViews(10, baseTime);
			List<ExpiredUrlView> secondBatch = createExpiredUrlViews(5, baseTime.plusHours(1));

			when(shortUrlRepository.selectShortUrlsWithPagination(
				isNull(), any(LocalDateTime.class), isNull(), eq(batchSize)
			)).thenReturn(firstBatch);

			ExpiredUrlView lastOfFirstBatch = firstBatch.get(9);
			when(shortUrlRepository.selectShortUrlsWithPagination(
				eq(lastOfFirstBatch.getId()), 
				any(LocalDateTime.class), 
				eq(lastOfFirstBatch.getExpiredAt()), 
				eq(batchSize)
			)).thenReturn(secondBatch);

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			verify(shortUrlRepository).selectShortUrlsWithPagination(
				eq(lastOfFirstBatch.getId()), 
				any(LocalDateTime.class), 
				eq(lastOfFirstBatch.getExpiredAt()), 
				eq(batchSize)
			);
		}
	}

	/**
	 * 테스트용 ExpiredUrlView 목록 생성
	 */
	private List<ExpiredUrlView> createExpiredUrlViews(int count, LocalDateTime baseTime) {
		List<ExpiredUrlView> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			long id = i + 1;
			LocalDateTime expiredAt = baseTime.plusMinutes(i);
			list.add(createExpiredUrlView(id, expiredAt));
		}
		return list;
	}

	/**
	 * 단일 ExpiredUrlView 생성
	 */
	private ExpiredUrlView createExpiredUrlView(Long id, LocalDateTime expiredAt) {
		return new ExpiredUrlView() {
			@Override
			public Long getId() {
				return id;
			}

			@Override
			public LocalDateTime getExpiredAt() {
				return expiredAt;
			}
		};
	}
}
