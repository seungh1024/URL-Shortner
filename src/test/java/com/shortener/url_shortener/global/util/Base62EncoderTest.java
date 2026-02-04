package com.shortener.url_shortener.global.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base62Encoder 단위 테스트
 * 
 * 테스트 내용:
 * - 인코딩/디코딩 정확성
 * - 특수문자 포함 여부
 * - 엣지 케이스 처리
 */
@DisplayName("Base62Encoder 단위 테스트")
class Base62EncoderTest {

	private Base62Encoder encoder;

	@BeforeEach
	void setUp() {
		encoder = new Base62Encoder();
	}

	@Nested
	@DisplayName("byte 배열 인코딩 테스트")
	class EncodeByteArrayTest {

		@Test
		@DisplayName("일반 바이트 배열 인코딩 성공")
		void encodeByteArray_success() {
			// given
			byte[] input = "test".getBytes(StandardCharsets.UTF_8);

			// when
			String result = encoder.encode(input);

			// then
			assertNotNull(result);
			assertFalse(result.isEmpty());
			assertTrue(encoder.isValid(result));
		}

		@Test
		@DisplayName("인코딩 결과는 Base62 문자만 포함 (특수문자 없음)")
		void encoded_containsOnlyBase62Chars() {
			// given
			byte[] input = "Hello, World! 123 @#$".getBytes(StandardCharsets.UTF_8);

			// when
			String result = encoder.encode(input);

			// then
			assertTrue(result.matches("^[0-9a-zA-Z]+$"), "Should contain only 0-9, a-z, A-Z");
			assertFalse(result.contains("+"));
			assertFalse(result.contains("/"));
			assertFalse(result.contains("="));
			assertFalse(result.contains(" "));
		}

		@Test
		@DisplayName("빈 배열 인코딩 시 빈 문자열 반환")
		void emptyArray_returnsEmptyString() {
			// given
			byte[] input = new byte[0];

			// when
			String result = encoder.encode(input);

			// then
			assertEquals("", result);
		}

		@Test
		@DisplayName("null 배열 인코딩 시 빈 문자열 반환")
		void nullArray_returnsEmptyString() {
			// given
			byte[] input = null;

			// when
			String result = encoder.encode(input);

			// then
			assertEquals("", result);
		}

		@Test
		@DisplayName("SHA-256 해시 결과 인코딩 (32 bytes → ~43 chars)")
		void encodeSha256Hash() {
			// given
			byte[] sha256Hash = new byte[32];  // SHA-256은 32 bytes
			for (int i = 0; i < 32; i++) {
				sha256Hash[i] = (byte) i;
			}

			// when
			String result = encoder.encode(sha256Hash);

			// then
			assertNotNull(result);
			assertTrue(result.length() >= 40 && result.length() <= 45, 
				"Base62 encoded SHA-256 should be ~43 chars");
			assertTrue(encoder.isValid(result));
		}
	}

	@Nested
	@DisplayName("byte 배열 디코딩 테스트")
	class DecodeTest {

		@Test
		@DisplayName("인코딩 후 디코딩하면 원본 복원")
		void encodeAndDecode_restoresOriginal() {
			// given
			byte[] original = "Hello Base62!".getBytes(StandardCharsets.UTF_8);

			// when
			String encoded = encoder.encode(original);
			byte[] decoded = encoder.decode(encoded);

			// then
			assertArrayEquals(original, decoded);
		}

		@Test
		@DisplayName("빈 문자열 디코딩 시 빈 배열 반환")
		void emptyString_returnsEmptyArray() {
			// given
			String input = "";

			// when
			byte[] result = encoder.decode(input);

			// then
			assertEquals(0, result.length);
		}

		@Test
		@DisplayName("null 문자열 디코딩 시 빈 배열 반환")
		void nullString_returnsEmptyArray() {
			// given
			String input = null;

			// when
			byte[] result = encoder.decode(input);

			// then
			assertEquals(0, result.length);
		}

		@Test
		@DisplayName("잘못된 Base62 문자 포함 시 예외 발생")
		void invalidBase62Char_throwsException() {
			// given
			String invalidInput = "abc+xyz";  // '+' is not Base62

			// when & then
			assertThrows(IllegalArgumentException.class, () -> encoder.decode(invalidInput));
		}

		@Test
		@DisplayName("특수문자 포함 시 예외 발생")
		void specialChars_throwException() {
			// given
			String[] invalidInputs = {"abc/def", "abc=def", "abc def", "abc@def"};

			// when & then
			for (String invalid : invalidInputs) {
				assertThrows(IllegalArgumentException.class, 
					() -> encoder.decode(invalid),
					"Should throw exception for: " + invalid);
			}
		}
	}

	@Nested
	@DisplayName("Long 값 인코딩 테스트")
	class EncodeLongTest {

		@Test
		@DisplayName("양수 Long 값 인코딩")
		void encodePositiveLong() {
			// given
			long input = 123456789L;

			// when
			String result = encoder.encode(input);

			// then
			assertNotNull(result);
			assertFalse(result.isEmpty());
			assertTrue(encoder.isValid(result));
		}

		@Test
		@DisplayName("0 인코딩")
		void encodeZero() {
			// given
			long input = 0L;

			// when
			String result = encoder.encode(input);

			// then
			assertEquals("0", result);
		}

		@Test
		@DisplayName("큰 숫자 인코딩 (TSID)")
		void encodeLargeLong() {
			// given
			long tsid = 123456789012345678L;

			// when
			String result = encoder.encode(tsid);

			// then
			assertNotNull(result);
			assertTrue(result.length() > 0);
			assertTrue(encoder.isValid(result));
		}
	}

	@Nested
	@DisplayName("검증 메서드 테스트")
	class ValidationTest {

		@Test
		@DisplayName("유효한 Base62 문자열")
		void validBase62String() {
			// given
			String[] validStrings = {
				"abc123",
				"ABC123",
				"aB3Xy9Km",
				"0123456789",
				"abcdefghijklmnopqrstuvwxyz",
				"ABCDEFGHIJKLMNOPQRSTUVWXYZ"
			};

			// when & then
			for (String valid : validStrings) {
				assertTrue(encoder.isValid(valid), "Should be valid: " + valid);
			}
		}

		@Test
		@DisplayName("유효하지 않은 문자열")
		void invalidStrings() {
			// given
			String[] invalidStrings = {
				"abc+def",   // '+' 포함
				"abc/def",   // '/' 포함
				"abc=def",   // '=' 포함
				"abc def",   // 공백 포함
				"abc-def",   // '-' 포함
				"abc_def",   // '_' 포함
				"abc@def",   // '@' 포함
				"",          // 빈 문자열
				null         // null
			};

			// when & then
			for (String invalid : invalidStrings) {
				assertFalse(encoder.isValid(invalid), "Should be invalid: " + invalid);
			}
		}
	}

	@Nested
	@DisplayName("URL-safe 검증")
	class UrlSafeTest {

		@Test
		@DisplayName("인코딩 결과는 URL에 안전")
		void encoded_isUrlSafe() {
			// given
			byte[] input = "Test URL safety!".getBytes(StandardCharsets.UTF_8);

			// when
			String encoded = encoder.encode(input);

			// then
			// URL에서 문제가 되는 문자들이 없어야 함
			assertFalse(encoded.contains("/"));
			assertFalse(encoded.contains("+"));
			assertFalse(encoded.contains("="));
			assertFalse(encoded.contains("?"));
			assertFalse(encoded.contains("&"));
			assertFalse(encoded.contains("#"));
			assertFalse(encoded.contains(" "));
		}
	}
}
