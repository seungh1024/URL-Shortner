package com.shortener.url_shortener.domain.url.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shortener.url_shortener.domain.url.dto.ExpiredUrlView;
import com.shortener.url_shortener.domain.url.entity.ShortUrl;

@Repository
public interface ShortUrlJpaRepository extends JpaRepository<ShortUrl, Long> {

	Optional<ShortUrl> findByHashKey(String key);

	int deleteByHashKey(String hashKey);

	@Query(value = """
			SELECT expired_at, id
			FROM url_shortener
			WHERE expired_at <= :maxExpirationTime
			ORDER BY expired_at ASC, id ASC
			LIMIT :size
		""", nativeQuery = true)
	List<ExpiredUrlView> findExpiredUrlIdsFirstPage(@Param("maxExpirationTime") LocalDateTime maxExpirationTime,
		@Param("size") int size);

	@Query(value = """
			SELECT expired_at, id
			FROM url_shortener
			WHERE expired_at <= :maxExpirationTime
				AND (
					expired_at > :lastExpirationTime
					OR (expired_at = :lastExpirationTime AND id > :lastId)
				)
			ORDER BY expired_at ASC, id ASC
			LIMIT :size
		""", nativeQuery = true)
	List<ExpiredUrlView> findExpiredUrlIdsAfter(@Param("lastId") Long lastId,
		@Param("maxExpirationTime") LocalDateTime maxExpirationTime,
		@Param("lastExpirationTime") LocalDateTime lastExpirationTime, @Param("size") int size);

}
