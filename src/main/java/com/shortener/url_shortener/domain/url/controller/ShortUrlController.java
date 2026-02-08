package com.shortener.url_shortener.domain.url.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shortener.url_shortener.domain.url.dto.request.ShortUrlCreateRequest;
import com.shortener.url_shortener.domain.url.dto.response.ShortUrlCreateResponse;
import com.shortener.url_shortener.domain.url.service.ShortUrlService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/link")
@Validated
public class ShortUrlController {

	private final ShortUrlService shortUrlService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ShortUrlCreateResponse createLink(@Valid @RequestBody ShortUrlCreateRequest request) {
		return shortUrlService.createLink(request.redirectUrl());

	}

	@GetMapping("/{key}")
	public ResponseEntity<Void> getLink(@PathVariable String key) {
		String redirectURL = shortUrlService.getLink(key);
		HttpHeaders headers = new HttpHeaders();
		headers.setLocation(URI.create(redirectURL));

		return new ResponseEntity<>(headers, HttpStatus.FOUND);
	}
}
