package com.shortener.url_shortener.global.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HashGenerator 단위 테스트
 * 
 * 테스트 내용:
 * - SHA-256 해싱 정확성
 * - 결과 크기 검증
 * - 동일 입력에 대한 일관성
 */
@DisplayName("HashGenerator 단위 테스트")
class HashGeneratorTest {

	private HashGenerator generator;

	@BeforeEach
	void setUp() {
		generator = new HashGenerator();
	}

	@Nested
	@DisplayName("문자열 해싱 테스트")
	class HashStringTest {

		@Test
		@DisplayName("문자열 해싱 성공 (32 bytes 반환)")
		void hashString_returns32Bytes() {
			// given
			String input = "test string";

			// when
			byte[] result = generator.hash(input);

			// then
			assertNotNull(result);
			assertEquals(32, result.length, "SHA-256 should return 32 bytes");
		}

		@Test
		@DisplayName("동일한 입력은 동일한 해시 생성")
		void sameInput_producesSameHash() {
			// given
			String input = "https://example.com";

			// when
			byte[] hash1 = generator.hash(input);
			byte[] hash2 = generator.hash(input);

			// then
			assertArrayEquals(hash1, hash2);
		}

		@Test
		@DisplayName("다른 입력은 다른 해시 생성")
		void differentInput_producesDifferentHash() {
			// given
			String input1 = "https://example.com";
			String input2 = "https://example.org";

			// when
			byte[] hash1 = generator.hash(input1);
			byte[] hash2 = generator.hash(input2);

			// then
			assertFalse(java.util.Arrays.equals(hash1, hash2));
		}

		@Test
		@DisplayName("빈 문자열도 해싱 가능")
		void emptyString_canBeHashed() {
			// given
			String input = "";

			// when
			byte[] result = generator.hash(input);

			// then
			assertNotNull(result);
			assertEquals(32, result.length);
		}

		@Test
		@DisplayName("긴 문자열도 32 bytes 해시 생성")
		void longString_produces32BytesHash() {
			// given
			String input = "a".repeat(10000);

			// when
			byte[] result = generator.hash(input);

			// then
			assertEquals(32, result.length);
		}

		@Test
		@DisplayName("특수문자 포함 문자열 해싱")
		void specialChars_canBeHashed() {
			// given
			String input = "Hello! @#$%^&*() 你好 🎉";

			// when
			byte[] result = generator.hash(input);

			// then
			assertNotNull(result);
			assertEquals(32, result.length);
		}
	}

	@Nested
	@DisplayName("TSID + URL 해싱 테스트")
	class HashTsidAndUrlTest {

		@Test
		@DisplayName("TSID + URL 해싱 성공")
		void hashTsidAndUrl_success() {
			// given
			Long tsid = 123456789L;
			String url = "https://example.com";

			// when
			byte[] result = generator.hash(tsid, url);

			// then
			assertNotNull(result);
			assertEquals(32, result.length);
		}

		@Test
		@DisplayName("동일한 TSID와 URL은 동일한 해시")
		void sameTsidAndUrl_producesSameHash() {
			// given
			Long tsid = 123456789L;
			String url = "https://example.com";

			// when
			byte[] hash1 = generator.hash(tsid, url);
			byte[] hash2 = generator.hash(tsid, url);

			// then
			assertArrayEquals(hash1, hash2);
		}

		@Test
		@DisplayName("다른 TSID는 다른 해시 생성")
		void differentTsid_producesDifferentHash() {
			// given
			Long tsid1 = 123456789L;
			Long tsid2 = 987654321L;
			String url = "https://example.com";

			// when
			byte[] hash1 = generator.hash(tsid1, url);
			byte[] hash2 = generator.hash(tsid2, url);

			// then
			assertFalse(java.util.Arrays.equals(hash1, hash2));
		}

		@Test
		@DisplayName("다른 URL은 다른 해시 생성")
		void differentUrl_producesDifferentHash() {
			// given
			Long tsid = 123456789L;
			String url1 = "https://example.com";
			String url2 = "https://example.org";

			// when
			byte[] hash1 = generator.hash(tsid, url1);
			byte[] hash2 = generator.hash(tsid, url2);

			// then
			assertFalse(java.util.Arrays.equals(hash1, hash2));
		}
	}

	@Nested
	@DisplayName("TSID + URL + Counter 해싱 테스트")
	class HashWithCounterTest {

		@Test
		@DisplayName("Counter 포함 해싱 성공")
		void hashWithCounter_success() {
			// given
			Long tsid = 123456789L;
			String url = "https://example.com";
			int counter = 1;

			// when
			byte[] result = generator.hash(tsid, url, counter);

			// then
			assertNotNull(result);
			assertEquals(32, result.length);
		}

		@Test
		@DisplayName("다른 Counter는 다른 해시 생성 (충돌 재시도용)")
		void differentCounter_producesDifferentHash() {
			// given
			Long tsid = 123456789L;
			String url = "https://example.com";

			// when
			byte[] hash1 = generator.hash(tsid, url, 0);
			byte[] hash2 = generator.hash(tsid, url, 1);
			byte[] hash3 = generator.hash(tsid, url, 2);

			// then
			assertFalse(java.util.Arrays.equals(hash1, hash2));
			assertFalse(java.util.Arrays.equals(hash2, hash3));
			assertFalse(java.util.Arrays.equals(hash1, hash3));
		}

		@Test
		@DisplayName("Counter를 통한 충돌 회피 검증")
		void counterHelpsAvoidCollision() {
			// given
			Long tsid = 123456789L;
			String url = "https://example.com";

			// when
			// Counter 0, 1, 2로 각각 다른 해시 생성
			byte[] baseHash = generator.hash(tsid, url);
			byte[] retry1Hash = generator.hash(tsid, url, 1);
			byte[] retry2Hash = generator.hash(tsid, url, 2);

			// then
			// 모두 다른 해시여야 함
			assertFalse(java.util.Arrays.equals(baseHash, retry1Hash));
			assertFalse(java.util.Arrays.equals(retry1Hash, retry2Hash));
		}
	}

	@Nested
	@DisplayName("16진수 변환 테스트")
	class ToHexStringTest {

		@Test
		@DisplayName("해시를 16진수 문자열로 변환 (64자리)")
		void toHexString_returns64Chars() {
			// given
			byte[] hash = generator.hash("test");

			// when
			String hex = generator.toHexString(hash);

			// then
			assertNotNull(hex);
			assertEquals(64, hex.length(), "32 bytes = 64 hex chars");
		}

		@Test
		@DisplayName("16진수 문자열은 0-9, a-f만 포함")
		void hexString_containsOnlyHexChars() {
			// given
			byte[] hash = generator.hash("test");

			// when
			String hex = generator.toHexString(hash);

			// then
			assertTrue(hex.matches("^[0-9a-f]+$"), "Should contain only 0-9 and a-f");
		}

		@Test
		@DisplayName("동일한 해시는 동일한 16진수 문자열")
		void sameHash_producesSameHexString() {
			// given
			byte[] hash = generator.hash("test");

			// when
			String hex1 = generator.toHexString(hash);
			String hex2 = generator.toHexString(hash);

			// then
			assertEquals(hex1, hex2);
		}
	}

	@Nested
	@DisplayName("해시 결과 분포 테스트")
	class HashDistributionTest {

		@Test
		@DisplayName("순차적 입력도 고르게 분포된 해시 생성")
		void sequentialInputs_produceDistributedHashes() {
			// given
			int count = 100;
			byte[][] hashes = new byte[count][];

			// when
			for (int i = 0; i < count; i++) {
				hashes[i] = generator.hash("url" + i);
			}

			// then
			// 모든 해시가 서로 다른지 확인
			for (int i = 0; i < count; i++) {
				for (int j = i + 1; j < count; j++) {
					assertFalse(java.util.Arrays.equals(hashes[i], hashes[j]),
						"Hash collision detected at " + i + " and " + j);
				}
			}
		}
	}
}
