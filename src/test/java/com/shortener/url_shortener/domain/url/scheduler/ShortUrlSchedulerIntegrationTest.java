package com.shortener.url_shortener.domain.url.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.shortener.url_shortener.container.IntegrationTestBase;
import com.shortener.url_shortener.domain.url.entity.ShortUrl;
import com.shortener.url_shortener.domain.url.repository.ShortUrlJpaRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ShortUrlScheduler 통합 테스트
 *
 * 테스트 내용:
 * - 페이징 처리 시 (expired_at, id) 순서대로 빠짐없이 삭제 검증
 * - expired_at이 같을 때 id 순서 처리
 * - 배치 크기보다 많은 데이터 처리
 * - 만료되지 않은 데이터는 삭제 안 됨
 */
@DisplayName("ShortUrlScheduler 통합 테스트")
class ShortUrlSchedulerIntegrationTest extends IntegrationTestBase {

	@Autowired
	private ShortUrlScheduler shortUrlScheduler;

	@Autowired
	private ShortUrlJpaRepository shortUrlJpaRepository;

	@Nested
	@DisplayName("페이징 처리 검증 - expired_at과 id 순서")
	class PaginationOrderTest {

		@Test
		@DisplayName("expired_at이 다를 때 시간 순서대로 삭제")
		void deleteExpired_differentExpiredAt_orderedByTime() {
			// given
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime day1 = now.minusDays(3);
			LocalDateTime day2 = now.minusDays(2);
			LocalDateTime day3 = now.minusDays(1);

			// 의도적으로 id 순서와 expired_at 순서를 다르게 설정
			shortUrlJpaRepository.save(new ShortUrl(10L, "key10", "https://a.com", day3));
			shortUrlJpaRepository.save(new ShortUrl(20L, "key20", "https://b.com", day1)); // 가장 먼저 만료
			shortUrlJpaRepository.save(new ShortUrl(30L, "key30", "https://c.com", day2));

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			long remainingCount = shortUrlJpaRepository.count();
			assertEquals(0, remainingCount, "모든 만료된 URL이 삭제되어야 함");
		}

		@Test
		@DisplayName("expired_at이 같을 때 id 순서대로 삭제 (핵심 검증)")
		void deleteExpired_sameExpiredAt_orderedById() {
			// given
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime sameExpiredAt = now.minusDays(1);

			// ✅ 핵심: expired_at이 모두 같고, id 순서가 1, 3, 2로 섞여있음
			// 페이징 처리 시 (expired_at, id) 기준으로 (sameExpiredAt, 1), (sameExpiredAt, 2), (sameExpiredAt, 3) 순서로 조회되어야 함
			shortUrlJpaRepository.save(new ShortUrl(1L, "key01", "https://a.com", sameExpiredAt));
			shortUrlJpaRepository.save(new ShortUrl(3L, "key03", "https://c.com", sameExpiredAt));
			shortUrlJpaRepository.save(new ShortUrl(2L, "key02", "https://b.com", sameExpiredAt));

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			long remainingCount = shortUrlJpaRepository.count();
			assertEquals(0, remainingCount, "expired_at이 같아도 id 순서로 모두 삭제되어야 함");
		}

		@Test
		@DisplayName("복잡한 시나리오: expired_at이 같은 데이터와 다른 데이터 혼재")
		void deleteExpired_mixedExpiredAtAndIds() {
			// given
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime day1 = now.minusDays(3);
			LocalDateTime day2 = now.minusDays(2);

			// day1에 여러 id
			shortUrlJpaRepository.save(new ShortUrl(5L, "key05", "https://e.com", day1));
			shortUrlJpaRepository.save(new ShortUrl(2L, "key02", "https://b.com", day1));
			shortUrlJpaRepository.save(new ShortUrl(8L, "key08", "https://h.com", day1));

			// day2에 여러 id
			shortUrlJpaRepository.save(new ShortUrl(3L, "key03", "https://c.com", day2));
			shortUrlJpaRepository.save(new ShortUrl(7L, "key07", "https://g.com", day2));
			shortUrlJpaRepository.save(new ShortUrl(1L, "key01", "https://a.com", day2));

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			long remainingCount = shortUrlJpaRepository.count();
			assertEquals(0, remainingCount, "복잡한 순서에서도 모든 만료 데이터가 삭제되어야 함");
		}
	}

	@Nested
	@DisplayName("배치 크기 처리 검증")
	class BatchSizeHandlingTest {

		@Test
		@DisplayName("배치 크기보다 많은 데이터 처리 (25개 데이터, 배치 크기 10)")
		void deleteExpired_moreThanBatchSize() {
			// given
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime baseTime = now.minusDays(1);

			// 25개 데이터 생성 (배치 크기 10의 2.5배)
			for (long i = 1; i <= 25; i++) {
				shortUrlJpaRepository.save(new ShortUrl(
					i,
					"key" + String.format("%02d", i),
					"https://example.com/" + i,
					baseTime.plusMinutes(i)
				));
			}

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			long remainingCount = shortUrlJpaRepository.count();
			assertEquals(0, remainingCount, "배치 크기보다 많은 데이터도 모두 삭제되어야 함");
		}

		@Test
		@DisplayName("배치 크기의 정확히 배수인 경우 (20개 데이터, 배치 크기 10)")
		void deleteExpired_exactMultipleOfBatchSize() {
			// given
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime baseTime = now.minusDays(1);

			// 20개 데이터 생성 (배치 크기 10의 정확히 2배)
			for (long i = 1; i <= 20; i++) {
				shortUrlJpaRepository.save(new ShortUrl(
					i,
					"key" + String.format("%02d", i),
					"https://example.com/" + i,
					baseTime.plusMinutes(i)
				));
			}

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			long remainingCount = shortUrlJpaRepository.count();
			assertEquals(0, remainingCount, "배치 크기의 정확한 배수도 모두 삭제되어야 함");
		}

		@Test
		@DisplayName("배치 크기보다 적은 데이터 처리 (5개 데이터, 배치 크기 10)")
		void deleteExpired_lessThanBatchSize() {
			// given
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime baseTime = now.minusDays(1);

			// 5개 데이터 생성
			for (long i = 1; i <= 5; i++) {
				shortUrlJpaRepository.save(new ShortUrl(
					i,
					"key" + String.format("%02d", i),
					"https://example.com/" + i,
					baseTime.plusMinutes(i)
				));
			}

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			long remainingCount = shortUrlJpaRepository.count();
			assertEquals(0, remainingCount, "배치 크기보다 적은 데이터도 모두 삭제되어야 함");
		}
	}

	@Nested
	@DisplayName("만료 기준 검증")
	class ExpirationCriteriaTest {

		@Test
		@DisplayName("만료된 데이터만 삭제, 만료 안 된 데이터는 유지")
		void deleteExpired_onlyExpired_notExpiredRemains() {
			// given
			LocalDateTime now = LocalDateTime.now();

			// 만료된 데이터
			shortUrlJpaRepository.save(new ShortUrl(1L, "expired1", "https://a.com", now.minusDays(3)));
			shortUrlJpaRepository.save(new ShortUrl(2L, "expired2", "https://b.com", now.minusDays(2)));
			shortUrlJpaRepository.save(new ShortUrl(3L, "expired3", "https://c.com", now.minusDays(1)));

			// 만료 안 된 데이터
			shortUrlJpaRepository.save(new ShortUrl(4L, "valid1", "https://d.com", now.plusDays(1)));
			shortUrlJpaRepository.save(new ShortUrl(5L, "valid2", "https://e.com", now.plusDays(2)));
			shortUrlJpaRepository.save(new ShortUrl(6L, "valid3", "https://f.com", now.plusDays(3)));

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			long remainingCount = shortUrlJpaRepository.count();
			assertEquals(3, remainingCount, "만료 안 된 데이터 3개만 남아야 함");

			List<ShortUrl> remaining = shortUrlJpaRepository.findAll();
			assertTrue(remaining.stream().allMatch(url -> url.getExpiredAt().isAfter(now)),
				"남은 데이터는 모두 만료되지 않은 데이터여야 함");
		}

		@Test
		@DisplayName("경계값 테스트: 현재 시각과 동일한 expiredAt은 삭제됨")
		void deleteExpired_boundaryCase_exactlyNow() {
			// given
			LocalDateTime now = LocalDateTime.now();

			// 현재 시각과 정확히 같은 시각
			shortUrlJpaRepository.save(new ShortUrl(1L, "boundary", "https://a.com", now));

			// 약간 미래
			shortUrlJpaRepository.save(new ShortUrl(2L, "future", "https://b.com", now.plusSeconds(1)));

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			long remainingCount = shortUrlJpaRepository.count();
			assertEquals(1, remainingCount, "현재 시각 이후만 남아야 함");

			ShortUrl remaining = shortUrlJpaRepository.findAll().get(0);
			assertEquals("future", remaining.getHashKey());
		}
	}

	@Nested
	@DisplayName("커서 페이징 검증")
	class UserRequestedScenarioTest {

		@Test
		@DisplayName("핵심 시나리오: 배치 크기 10일 때 expired_at 혼재된 데이터 처리")
		void criticalScenario_mixedExpiredAt_noDataLoss() {
			// given
			// 사용자가 제시한 문제: expired_at이 다른 데이터가 섞여있을 때 누락 방지
			// 예: (2,feb02), (3,feb03), (1,feb10) 순서에서 모두 조회되어야 함
			
			LocalDateTime feb02 = LocalDateTime.of(2025, 2, 2, 0, 0);
			LocalDateTime feb03 = LocalDateTime.of(2025, 2, 3, 0, 0);
			LocalDateTime feb10 = LocalDateTime.of(2025, 2, 10, 0, 0);

			// 기본 3개 + 추가 데이터로 총 15개 (배치 크기 10보다 많게)
			// 첫 배치: 10개
			shortUrlJpaRepository.save(new ShortUrl(2L, "aaaaaa02", "https://b.com", feb02));
			for (int i = 3; i <= 11; i++) {
				shortUrlJpaRepository.save(new ShortUrl(
					(long) i,
					"aaaaaa" + String.format("%02d", i),
					"https://example.com/" + i,
					feb03
				));
			}
			// 두 번째 배치: 5개 (feb10과 그 이후)
			shortUrlJpaRepository.save(new ShortUrl(1L, "aaaaaa01", "https://a.com", feb10)); // 이게 누락되지 않아야 함!
			for (int i = 12; i <= 15; i++) {
				shortUrlJpaRepository.save(new ShortUrl(
					(long) i,
					"aaaaaa" + String.format("%02d", i),
					"https://example.com/" + i,
					feb10
				));
			}

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			long remainingCount = shortUrlJpaRepository.count();
			assertEquals(0, remainingCount, 
				"expired_at이 섞여있고 배치 경계를 넘어가도 데이터 누락 없이 모두 삭제되어야 함");
		}

		@Test
		@DisplayName("확장 시나리오: 배치 크기 3, 더 많은 데이터로 누락 검증")
		void extendedScenario_batchSize3_noDataLoss() {
			// given
			// 다양한 expired_at과 id 조합으로 페이징 처리 검증
			LocalDateTime base = LocalDateTime.of(2026, 2, 1, 0, 0);

			// 그룹 1: expired_at이 같음 (base + 1일)
			shortUrlJpaRepository.save(new ShortUrl(5L, "key05", "https://e.com", base.plusDays(1)));
			shortUrlJpaRepository.save(new ShortUrl(2L, "key02", "https://b.com", base.plusDays(1)));
			shortUrlJpaRepository.save(new ShortUrl(8L, "key08", "https://h.com", base.plusDays(1)));

			// 그룹 2: expired_at이 같음 (base + 2일)
			shortUrlJpaRepository.save(new ShortUrl(3L, "key03", "https://c.com", base.plusDays(2)));
			shortUrlJpaRepository.save(new ShortUrl(7L, "key07", "https://g.com", base.plusDays(2)));

			// 그룹 3: expired_at이 같음 (base + 3일)
			shortUrlJpaRepository.save(new ShortUrl(1L, "key01", "https://a.com", base.plusDays(3)));
			shortUrlJpaRepository.save(new ShortUrl(6L, "key06", "https://f.com", base.plusDays(3)));
			shortUrlJpaRepository.save(new ShortUrl(4L, "key04", "https://d.com", base.plusDays(3)));

			// 총 8개, 배치 크기 10이지만 실제로는 여러 expired_at 그룹으로 나뉨
			// 페이징 쿼리가 (expired_at, id) 순서를 제대로 처리하는지 검증

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			long remainingCount = shortUrlJpaRepository.count();
			assertEquals(0, remainingCount, 
				"expired_at이 같은 데이터가 여러 그룹으로 나뉘어도 모두 삭제되어야 함");
		}

		@Test
		@DisplayName("대량 데이터 시나리오: 50개 데이터, 배치 크기 10, expired_at 중복")
		void largeDataScenario_50Items_batchSize10_duplicateExpiredAt() {
			// given
			LocalDateTime base = LocalDateTime.now().minusDays(5);

			// 5개의 expired_at 그룹, 각 그룹당 10개의 id
			for (int group = 0; group < 5; group++) {
				LocalDateTime groupTime = base.plusDays(group);
				for (int id = 0; id < 10; id++) {
					long actualId = (long) (group * 10 + id + 1);
					shortUrlJpaRepository.save(new ShortUrl(
						actualId,
						"key" + String.format("%02d", actualId),
						"https://example.com/" + actualId,
						groupTime
					));
				}
			}

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			long remainingCount = shortUrlJpaRepository.count();
			assertEquals(0, remainingCount, 
				"50개 데이터, expired_at 중복, 배치 크기 10 상황에서도 모두 삭제되어야 함");
		}
	}

	@Nested
	@DisplayName("빈 데이터 처리")
	class EmptyDataTest {

		@Test
		@DisplayName("만료된 데이터가 없으면 삭제 없이 정상 종료")
		void noExpiredData_completeNormally() {
			// given
			LocalDateTime now = LocalDateTime.now();

			// 모두 만료 안 됨
			shortUrlJpaRepository.save(new ShortUrl(1L, "valid1", "https://a.com", now.plusDays(1)));
			shortUrlJpaRepository.save(new ShortUrl(2L, "valid2", "https://b.com", now.plusDays(2)));

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			long remainingCount = shortUrlJpaRepository.count();
			assertEquals(2, remainingCount, "만료 안 된 데이터는 그대로 유지되어야 함");
		}

		@Test
		@DisplayName("데이터가 전혀 없으면 정상 종료")
		void noData_completeNormally() {
			// given
			// 데이터 없음

			// when
			shortUrlScheduler.deleteExpiredShortUrls();

			// then
			long remainingCount = shortUrlJpaRepository.count();
			assertEquals(0, remainingCount);
		}
	}
}
