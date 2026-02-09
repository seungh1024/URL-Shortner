package com.shortener.url_shortener.global.util;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.SecureRandom;


@Component
public class Base62Encoder {

    private static final String BASE62_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;

    /**
     * byte 배열을 Base62로 인코딩
     * SHA-256 해시 결과(32 bytes)를 인코딩하면 약 43자리 문자열 생성
     * 
     * @param input 인코딩할 byte 배열
     * @return Base62 인코딩된 문자열
     */
    public String encode(byte[] input) {
        if (input == null || input.length == 0) {
            return "";
        }

        // byte 배열을 BigInteger로 변환 (양수로)
        BigInteger num = new BigInteger(1, input);

        // BigInteger를 Base62로 변환
        StringBuilder encoded = new StringBuilder();
        
        while (num.compareTo(BigInteger.ZERO) > 0) {
            BigInteger remainder = num.mod(BigInteger.valueOf(BASE));
            encoded.insert(0, BASE62_CHARS.charAt(remainder.intValue()));
            num = num.divide(BigInteger.valueOf(BASE));
        }

        return encoded.toString();
    }

    /**
     * Base62 문자열을 byte 배열로 디코딩
     * 
     * @param input Base62 인코딩된 문자열
     * @return 디코딩된 byte 배열
     * @throws IllegalArgumentException Base62 문자가 아닌 문자가 포함된 경우
     */
    public byte[] decode(String input) {
        if (input == null || input.isEmpty()) {
            return new byte[0];
        }

        // Base62 문자열을 BigInteger로 변환
        BigInteger num = BigInteger.ZERO;
        
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            int index = BASE62_CHARS.indexOf(c);
            
            if (index == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            
            num = num.multiply(BigInteger.valueOf(BASE))
                     .add(BigInteger.valueOf(index));
        }

        return num.toByteArray();
    }

    /**
     * Base62 문자열이 유효한지 검증
     * 
     * @param input 검증할 문자열
     * @return 유효하면 true
     */
    public boolean isValid(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        for (char c : input.toCharArray()) {
            if (BASE62_CHARS.indexOf(c) == -1) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * 숫자를 Base62로 인코딩 (TSID 등의 Long 값 인코딩용)
     * 
     * @param number 인코딩할 숫자
     * @return Base62 인코딩된 문자열
     */
    public String encode(long number) {
        if (number == 0) {
            return String.valueOf(BASE62_CHARS.charAt(0));
        }

        StringBuilder encoded = new StringBuilder();
        long num = number;

        while (num > 0) {
            int remainder = (int) (num % BASE);
            encoded.insert(0, BASE62_CHARS.charAt(remainder));
            num = num / BASE;
        }

        return encoded.toString();
    }

    /**
     * Base62 랜덤 문자열 생성
     *
     * @param length 생성할 길이
     * @param random 사용할 난수 생성기
     * @return Base62 랜덤 문자열
     */
    public String random(int length, SecureRandom random) {
        if (length <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(BASE62_CHARS.charAt(random.nextInt(BASE)));
        }
        return sb.toString();
    }

}
