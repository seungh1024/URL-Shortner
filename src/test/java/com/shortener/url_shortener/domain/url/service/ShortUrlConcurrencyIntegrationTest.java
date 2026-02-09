package com.shortener.url_shortener.domain.url.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.shortener.url_shortener.container.IntegrationTestBase;
import com.shortener.url_shortener.domain.url.dto.response.ShortUrlCreateResponse;
import com.shortener.url_shortener.domain.url.entity.ShortUrl;
import com.shortener.url_shortener.domain.url.repository.ShortUrlJpaRepository;
import com.shortener.url_shortener.global.util.HashGenerator;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ShortUrl 동시성 통합 테스트")
class ShortUrlConcurrencyIntegrationTest extends IntegrationTestBase {

	@Autowired
	private ShortUrlService shortUrlService;

	@Autowired
	private ShortUrlJpaRepository shortUrlJpaRepository;

	@Autowired
	private HashGenerator hashGenerator;

	@Test
	@DisplayName("동일 URL 동시 요청 시 단 하나의 결과만 생성된다")
	void createLink_concurrentSameUrl_createsSingleRow() throws Exception {
		// given
		String redirectUrl = "https://example.com/concurrent";
		int threads = 10;
		int iterationsPerThread = 10;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<ShortUrlCreateResponse>> futures = new ArrayList<>();
		Set<String> shortCodes = new ConcurrentSkipListSet<>();
		ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

		for (int i = 0; i < threads; i++) {
			futures.add(executor.submit(() -> {
				ready.countDown();
				start.await(5, TimeUnit.SECONDS);
				ShortUrlCreateResponse lastResponse = null;
				for (int j = 0; j < iterationsPerThread; j++) {
					try {
						lastResponse = shortUrlService.createLink(redirectUrl);
						shortCodes.add(lastResponse.shortCode());
					} catch (Throwable e) {
						errors.add(e);
						break;
					}
				}
				return lastResponse;
			}));
		}

		ready.await(5, TimeUnit.SECONDS);
		start.countDown();

		for (Future<ShortUrlCreateResponse> future : futures) {
			future.get(30, TimeUnit.SECONDS);
		}

		executor.shutdown();
		executor.awaitTermination(10, TimeUnit.SECONDS);

		// then: 모든 응답이 동일 shortCode
		assertThat(errors).isEmpty();
		assertThat(shortCodes).hasSize(1);

		// then: DB에는 동일 URL에 대한 row가 1개만 존재
		byte[] hashKey = hashGenerator.hash(redirectUrl);
		List<ShortUrl> existing = shortUrlJpaRepository.findByHashKeyAndExpiredAtAfter(
			hashKey,
			LocalDateTime.now()
		);
		long sameUrlCount = existing.stream()
			.filter(item -> item.getRedirectionUrl().equals(redirectUrl))
			.count();

		assertThat(sameUrlCount).isEqualTo(1);
	}
}
