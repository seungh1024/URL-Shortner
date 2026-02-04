package com.shortener.url_shortener.domain.url.controller;

import com.shortener.url_shortener.domain.url.CreateLinkRequest;
import com.shortener.url_shortener.domain.url.CreateLinkResponse;
import com.shortener.url_shortener.domain.url.DeleteLinkRequest;
import com.shortener.url_shortener.domain.url.DeleteLinkResponse;
import com.shortener.url_shortener.domain.url.dto.response.LinkCreateResponse;
import com.shortener.url_shortener.domain.url.service.URLShortenerService;
import com.shortener.url_shortener.global.error.CustomException;
import com.shortener.url_shortener.global.error.ErrorCode;
import com.shortener.url_shortener.global.error.GrpcExceptionHandler;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UrlShortenerGrpcController 단위 테스트
 * 
 * 테스트 내용:
 * - createLink 성공/실패 케이스
 * - deleteLink 성공/실패 케이스
 * - GrpcExceptionHandler 통합 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UrlShortenerGrpcController 단위 테스트")
class UrlShortenerGrpcControllerTest {

	private UrlShortenerGrpcController controller;

	@Mock
	private URLShortenerService urlShortenerService;

	private GrpcExceptionHandler exceptionHandler;

	@Mock
	private StreamObserver<CreateLinkResponse> createLinkObserver;

	@Mock
	private StreamObserver<DeleteLinkResponse> deleteLinkObserver;

	@Captor
	private ArgumentCaptor<CreateLinkResponse> createLinkResponseCaptor;

	@Captor
	private ArgumentCaptor<DeleteLinkResponse> deleteLinkResponseCaptor;

	@Captor
	private ArgumentCaptor<StatusRuntimeException> exceptionCaptor;

	@BeforeEach
	void setUp() {
		exceptionHandler = new GrpcExceptionHandler();
		controller = new UrlShortenerGrpcController(urlShortenerService, exceptionHandler);
	}

	@Nested
	@DisplayName("createLink 테스트")
	class CreateLinkTest {

		@Test
		@DisplayName("성공: 정상적으로 단축 URL 생성")
		void createLink_success() {
			// given
			String redirectUrl = "https://example.com";
			String hashKey = "aB3Xy9Km";
			String shortUrl = "http://localhost:8080/aB3Xy9Km";

			CreateLinkRequest request = CreateLinkRequest.newBuilder()
				.setRedirectUrl(redirectUrl)
				.build();

			LinkCreateResponse serviceResponse = new LinkCreateResponse(hashKey, shortUrl);
			when(urlShortenerService.createLink(redirectUrl)).thenReturn(serviceResponse);

			// when
			controller.createLink(request, createLinkObserver);

			// then
			verify(urlShortenerService, times(1)).createLink(redirectUrl);
			verify(createLinkObserver, times(1)).onNext(createLinkResponseCaptor.capture());
			verify(createLinkObserver, times(1)).onCompleted();
			verify(createLinkObserver, never()).onError(any());

			CreateLinkResponse response = createLinkResponseCaptor.getValue();
			assertEquals(hashKey, response.getHashKey());
			assertEquals(shortUrl, response.getShortUrl());
		}

		@Test
		@DisplayName("실패: URL 생성 실패 시 RESOURCE_EXHAUSTED 에러")
		void createLink_failUrlGeneration() {
			// given
			String redirectUrl = "https://example.com";
			CreateLinkRequest request = CreateLinkRequest.newBuilder()
				.setRedirectUrl(redirectUrl)
				.build();

			CustomException exception = ErrorCode.URL_GENERATION_FAILED.baseException(
				"Hash collision occurred"
			);
			when(urlShortenerService.createLink(redirectUrl)).thenThrow(exception);

			// when
			controller.createLink(request, createLinkObserver);

			// then
			verify(urlShortenerService, times(1)).createLink(redirectUrl);
			verify(createLinkObserver, never()).onNext(any());
			verify(createLinkObserver, never()).onCompleted();
			verify(createLinkObserver, times(1)).onError(exceptionCaptor.capture());

			StatusRuntimeException error = exceptionCaptor.getValue();
			assertEquals(Status.Code.RESOURCE_EXHAUSTED, error.getStatus().getCode());
			assertTrue(error.getStatus().getDescription().contains("URL 생성"));
		}

		@Test
		@DisplayName("실패: 요청 취소 시 CANCELLED 에러")
		void createLink_requestCancelled() {
			// given
			String redirectUrl = "https://example.com";
			CreateLinkRequest request = CreateLinkRequest.newBuilder()
				.setRedirectUrl(redirectUrl)
				.build();

			CustomException exception = ErrorCode.REQUEST_CANCELLED.baseException(
				"Request was cancelled by client"
			);
			when(urlShortenerService.createLink(redirectUrl)).thenThrow(exception);

			// when
			controller.createLink(request, createLinkObserver);

			// then
			verify(createLinkObserver, times(1)).onError(exceptionCaptor.capture());

			StatusRuntimeException error = exceptionCaptor.getValue();
			assertEquals(Status.Code.CANCELLED, error.getStatus().getCode());
			assertTrue(error.getStatus().getDescription().contains("cancelled"));
		}

		@Test
		@DisplayName("실패: 예상치 못한 에러 시 INTERNAL 에러")
		void createLink_unexpectedError() {
			// given
			String redirectUrl = "https://example.com";
			CreateLinkRequest request = CreateLinkRequest.newBuilder()
				.setRedirectUrl(redirectUrl)
				.build();

			RuntimeException exception = new RuntimeException("Unexpected error");
			when(urlShortenerService.createLink(redirectUrl)).thenThrow(exception);

			// when
			controller.createLink(request, createLinkObserver);

			// then
			verify(createLinkObserver, times(1)).onError(exceptionCaptor.capture());

			StatusRuntimeException error = exceptionCaptor.getValue();
			assertEquals(Status.Code.INTERNAL, error.getStatus().getCode());
		}
	}

	@Nested
	@DisplayName("deleteLink 테스트")
	class DeleteLinkTest {

		@Test
		@DisplayName("성공: 정상적으로 단축 URL 삭제")
		void deleteLink_success() {
			// given
			String hashKey = "aB3Xy9Km";
			DeleteLinkRequest request = DeleteLinkRequest.newBuilder()
				.setHashKey(hashKey)
				.build();

			doNothing().when(urlShortenerService).deleteLink(hashKey);

			// when
			controller.deleteLink(request, deleteLinkObserver);

			// then
			verify(urlShortenerService, times(1)).deleteLink(hashKey);
			verify(deleteLinkObserver, times(1)).onNext(deleteLinkResponseCaptor.capture());
			verify(deleteLinkObserver, times(1)).onCompleted();
			verify(deleteLinkObserver, never()).onError(any());

			DeleteLinkResponse response = deleteLinkResponseCaptor.getValue();
			assertNotNull(response);
		}

		@Test
		@DisplayName("실패: 존재하지 않는 키 삭제 시 NOT_FOUND 에러")
		void deleteLink_keyNotFound() {
			// given
			String hashKey = "notExist";
			DeleteLinkRequest request = DeleteLinkRequest.newBuilder()
				.setHashKey(hashKey)
				.build();

			CustomException exception = ErrorCode.KEY_NOT_FOUND.baseException(
				"Key not found"
			);
			doThrow(exception).when(urlShortenerService).deleteLink(hashKey);

			// when
			controller.deleteLink(request, deleteLinkObserver);

			// then
			verify(urlShortenerService, times(1)).deleteLink(hashKey);
			verify(deleteLinkObserver, never()).onNext(any());
			verify(deleteLinkObserver, never()).onCompleted();
			verify(deleteLinkObserver, times(1)).onError(exceptionCaptor.capture());

			StatusRuntimeException error = exceptionCaptor.getValue();
			assertEquals(Status.Code.NOT_FOUND, error.getStatus().getCode());
		}

		@Test
		@DisplayName("실패: 잘못된 키 형식 시 INVALID_ARGUMENT 에러")
		void deleteLink_invalidKey() {
			// given
			String hashKey = "invalid@key!";
			DeleteLinkRequest request = DeleteLinkRequest.newBuilder()
				.setHashKey(hashKey)
				.build();

			CustomException exception = ErrorCode.INVALID_KEY_ERROR.baseException(
				"Invalid key format"
			);
			doThrow(exception).when(urlShortenerService).deleteLink(hashKey);

			// when
			controller.deleteLink(request, deleteLinkObserver);

			// then
			verify(deleteLinkObserver, times(1)).onError(exceptionCaptor.capture());

			StatusRuntimeException error = exceptionCaptor.getValue();
			assertEquals(Status.Code.INVALID_ARGUMENT, error.getStatus().getCode());
		}
	}

	@Nested
	@DisplayName("에러 변환 통합 테스트")
	class ErrorConversionTest {

		@Test
		@DisplayName("CustomException이 올바른 gRPC Status로 변환됨")
		void customException_convertsToCorrectStatus() {
			// given
			String redirectUrl = "https://example.com";
			CreateLinkRequest request = CreateLinkRequest.newBuilder()
				.setRedirectUrl(redirectUrl)
				.build();

			// BAD_REQUEST → INVALID_ARGUMENT
			CustomException badRequest = new CustomException(
				HttpStatus.BAD_REQUEST,
				"Bad request"
			);
			when(urlShortenerService.createLink(redirectUrl)).thenThrow(badRequest);

			// when
			controller.createLink(request, createLinkObserver);

			// then
			verify(createLinkObserver, times(1)).onError(exceptionCaptor.capture());
			StatusRuntimeException error = exceptionCaptor.getValue();
			assertEquals(Status.Code.INVALID_ARGUMENT, error.getStatus().getCode());
		}
	}
}
