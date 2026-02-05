package com.shortener.url_shortener.domain.url.scheduler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shortener.url_shortener.domain.url.dto.ExpiredUrlView;
import com.shortener.url_shortener.domain.url.repository.ShortUrlJpaRepository;
import com.shortener.url_shortener.domain.url.repository.ShortUrlRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShortUrlScheduler {

	private final ShortUrlRepository shortUrlRepository;
	private final ShortUrlJpaRepository shortUrlJpaRepository;

	@Value("${scheduler.expired-url-deletion.batch-size:500}")
	private int batchSize;


	@Scheduled(cron = "${scheduler.expired-url-deletion.cron}")
	public void deleteExpiredShortUrls() {
		log.info("Starting expired short URLs deletion scheduler");

		LocalDateTime maxExpirationTime = LocalDateTime.now();
		LocalDateTime lastExpirationTime = null;
		Long lastId = null;

		List<ExpiredUrlView> list = new ArrayList<>();

		int totalDeleted = 0;
		int totalFailed = 0;
		int batchCount = 0;

		do {
			try {
				list = shortUrlRepository.selectShortUrlsWithPagination(lastId, maxExpirationTime, lastExpirationTime,
					batchSize);
			} catch (Exception e) {
				log.error("Failed to fetch expired URLs. Stopping scheduler. "
					+ "lastId: {}, lastExpirationTime: {}, error: {}", lastId, lastExpirationTime, e.getMessage(), e);
				break; // 조회 실패면 더 이상 진행 불가
			}

			// 빈 배치면 종료
			if (list.isEmpty()) {
				break;
			}

			// 커서 업데이트 (삭제 실패해도 다음 배치로 진행 가능하도록)
			ExpiredUrlView lastDto = list.get(list.size() - 1);
			lastId = lastDto.getId();
			lastExpirationTime = lastDto.getExpiredAt();

			List<Long> ids = list.stream().map(ExpiredUrlView::getId).toList();

			try {
				shortUrlJpaRepository.deleteAllByIdInBatch(ids);
				totalDeleted += ids.size();
				batchCount++;

				log.info("Batch {} completed: deleted {} URLs (lastId: {}, lastExpirationTime: {})", batchCount,
					ids.size(), lastId, lastExpirationTime);
			} catch (Exception e) {
				totalFailed += ids.size();
				log.error("Failed to delete batch {}, but continuing to next batch. "
						+ "Failed count: {}, lastId: {}, lastExpirationTime: {}, error: {}", batchCount + 1, ids.size(),
					lastId, lastExpirationTime, e.getMessage(), e);
			}

		} while (list.size() == batchSize);

		log.info(
			"Expired short URLs deletion completed. " + "Total deleted: {}, Total failed: {}, Successful batches: {}",
			totalDeleted, totalFailed, batchCount);

	}
}
