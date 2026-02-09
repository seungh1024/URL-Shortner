package com.shortener.url_shortener.container;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 통합 테스트 베이스 클래스
 * 
 * 기능:
 * - MySQL Testcontainer 자동 실행
 * - 테스트 간 DB 초기화
 * - 공통 설정 제공
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

	private static final MySQLContainer<?> MYSQL_CONTAINER;

	static {
		MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.0")
			.withDatabaseName("url_shortener_test")
			.withUsername("test")
			.withPassword("test")
			.withReuse(true);
		MYSQL_CONTAINER.start();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (MYSQL_CONTAINER != null) {
				MYSQL_CONTAINER.stop();
			}
		}));
	}

	@DynamicPropertySource
	static void setProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
		registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
		registry.add("spring.datasource.driver-class-name", MYSQL_CONTAINER::getDriverClassName);
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * 각 테스트 전에 DB 초기화
	 */
	@BeforeEach
	void cleanUpDatabase() {
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
		jdbcTemplate.execute("TRUNCATE TABLE url_shortener");
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
	}
}
