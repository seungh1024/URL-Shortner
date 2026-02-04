package com.shortener.url_shortener.domain.url.controller;

import net.devh.boot.grpc.server.service.GrpcService;

import com.shortener.url_shortener.domain.url.CreateLinkRequest;
import com.shortener.url_shortener.domain.url.CreateLinkResponse;
import com.shortener.url_shortener.domain.url.DeleteLinkRequest;
import com.shortener.url_shortener.domain.url.DeleteLinkResponse;
import com.shortener.url_shortener.domain.url.UrlShortenerRpcGrpc;
import com.shortener.url_shortener.domain.url.dto.response.LinkCreateResponse;
import com.shortener.url_shortener.domain.url.service.URLShortenerService;
import com.shortener.url_shortener.global.error.GrpcExceptionHandler;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * URL 단축 gRPC Service
 *
 * 특징:
 * - gRPC Status 방식으로 일관된 에러 처리
 * - API Key 인증은 Interceptor에서 처리
 * - Context.isCancelled() 체크는 Service 계층에서 처리
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UrlShortenerGrpcController extends UrlShortenerRpcGrpc.UrlShortenerRpcImplBase {

	private final URLShortenerService urlShortenerService;
	private final GrpcExceptionHandler exceptionHandler;

	/**
	 * 단축 URL 생성
	 *
	 * @param request redirectUrl 포함
	 * @param responseObserver 응답 전송 객체
	 */
	@Override
	public void createLink(CreateLinkRequest request, StreamObserver<CreateLinkResponse> responseObserver) {
		try {
			log.info("[gRPC] createLink: redirectUrl={}", request.getRedirectUrl());

			// 비즈니스 로직 호출
			LinkCreateResponse serviceResponse = urlShortenerService.createLink(request.getRedirectUrl());

			// gRPC 응답 생성
			CreateLinkResponse grpcResponse = CreateLinkResponse.newBuilder()
				.setHashKey(serviceResponse.hashKey())
				.setShortUrl(serviceResponse.url())
				.build();

			responseObserver.onNext(grpcResponse);
			responseObserver.onCompleted();

			log.info("[gRPC] createLink success: hashKey={}", serviceResponse.hashKey());

		} catch (Exception e) {
			log.error("[gRPC] createLink error: {}", e.getMessage());
			// GrpcExceptionHandler가 Exception → gRPC Status 변환
			Status status = exceptionHandler.convertToStatus(e);
			responseObserver.onError(status.asRuntimeException());
		}
	}

	/**
	 * 단축 URL 삭제
	 *
	 * @param request hashKey 포함
	 * @param responseObserver 응답 전송 객체
	 */
	@Override
	public void deleteLink(DeleteLinkRequest request, StreamObserver<DeleteLinkResponse> responseObserver) {
		try {
			log.info("[gRPC] deleteLink: hashKey={}", request.getHashKey());

			urlShortenerService.deleteLink(request.getHashKey());

			// 빈 응답 (성공)
			DeleteLinkResponse grpcResponse = DeleteLinkResponse.newBuilder().build();

			responseObserver.onNext(grpcResponse);
			responseObserver.onCompleted();

			log.info("[gRPC] deleteLink success");

		} catch (Exception e) {
			log.error("[gRPC] deleteLink error: {}", e.getMessage());
			// GrpcExceptionHandler가 Exception → gRPC Status 변환
			Status status = exceptionHandler.convertToStatus(e);
			responseObserver.onError(status.asRuntimeException());
		}
	}
}