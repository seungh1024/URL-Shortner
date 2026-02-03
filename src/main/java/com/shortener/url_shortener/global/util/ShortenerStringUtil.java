package com.shortener.url_shortener.global.util;

import org.slf4j.helpers.MessageFormatter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ShortenerStringUtil {
	public static String format(String format, Object... objects) {
		return MessageFormatter.arrayFormat(format, objects).getMessage();
	}
}
