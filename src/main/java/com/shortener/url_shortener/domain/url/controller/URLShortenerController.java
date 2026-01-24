package com.shortener.url_shortener.domain.url.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shortener.url_shortener.domain.url.dto.request.LinkRequestDto;
import com.shortener.url_shortener.domain.url.service.URLShortenerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/link")
public class URLShortenerController {

	private final URLShortenerService urlShortenerService;

	@PostMapping
	public void makeLink(@RequestBody LinkRequestDto dto) {

	}

	@GetMapping("/{key}")
	public void getLink(@PathVariable String key) {
		urlShortenerService.getLine(key);
	}
}
