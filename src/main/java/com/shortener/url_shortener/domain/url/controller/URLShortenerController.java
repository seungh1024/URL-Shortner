package com.shortener.url_shortener.domain.url.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shortener.url_shortener.domain.url.dto.request.LinkRequest;
import com.shortener.url_shortener.domain.url.dto.response.LinkCreateResponse;
import com.shortener.url_shortener.domain.url.service.URLShortenerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/link")
public class URLShortenerController {

	private final URLShortenerService urlShortenerService;

	// @PostMapping
	// @ResponseStatus(HttpStatus.CREATED)
	// public LinkCreateResponse createLink(@RequestBody LinkRequest dto) {
	// 	return urlShortenerService.createLink(dto.redirectURL());
	// }

	@GetMapping("/{key}")
	public ResponseEntity<Void> getLink(@PathVariable String key) {
		String redirectURL = urlShortenerService.getLink(key);
		HttpHeaders headers = new HttpHeaders();
		headers.setLocation(URI.create(redirectURL));

		return new ResponseEntity<>(headers, HttpStatus.FOUND);
	}

	// @DeleteMapping("/{key}")
	// @ResponseStatus(HttpStatus.ACCEPTED)
	// public void deleteLink(@PathVariable String key) {
	// 	urlShortenerService.deleteLink(key);
	// }
}
