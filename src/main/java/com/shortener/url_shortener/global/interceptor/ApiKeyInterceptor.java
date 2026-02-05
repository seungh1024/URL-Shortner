package com.shortener.url_shortener.global.interceptor;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;

/**
 * gRPC API Key 인증 Interceptor
 *
 * 동작:
 * 1. 모든 gRPC 요청 가로채기 (@GrpcGlobalServerInterceptor)
 * 2. Metadata(헤더)에서 "x-api-key" 추출
 * 3. application.yml의 api-key와 비교
 * 4. 일치하면 통과, 불일치하면 UNAUTHENTICATED 에러
 *
 * 클라이언트 사용법:
 * Metadata metadata = new Metadata();
 * metadata.put(Metadata.Key.of("x-api-key", ...), "secret-key-12345");
 * stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
 */
@Slf4j
@GrpcGlobalServerInterceptor  // 이 애노테이션으로 자동 등록!
public class ApiKeyInterceptor implements ServerInterceptor {

	/**
	 * gRPC Metadata Key (헤더명)
	 * 클라이언트는 요청 시 이 이름으로 API Key를 전송
	 */
	private static final Metadata.Key<String> API_KEY_METADATA_KEY =
		Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

	/**
	 * 유효한 API Key (application.yml에서 주입)
	 * 현재는 스토리지 전용 서비스라 단일 키로 진행
	 */
	@Value("${grpc.server.api-key}")
	private String validApiKey;

	/**
	 * gRPC 요청 가로채기
	 *
	 * @param call 현재 RPC 호출 정보
	 * @param headers 요청 메타데이터 (HTTP 헤더와 유사)
	 * @param next 다음 핸들러 (검증 통과 시 호출)
	 * @return Listener
	 */
	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
		ServerCall<ReqT, RespT> call,
		Metadata headers,
		ServerCallHandler<ReqT, RespT> next) {

		String method = call.getMethodDescriptor().getFullMethodName();

		// 1. Metadata에서 API Key 추출
		String apiKey = headers.get(API_KEY_METADATA_KEY);

		log.debug("[gRPC] Interceptor - Method: {}, API Key: {}",
			method, apiKey != null ? "***" : "null");

		// 2. API Key 존재 여부 확인
		if (apiKey == null || apiKey.isEmpty()) {
			log.warn("[gRPC] API Key missing - Method: {}", method);
			call.close(
				Status.UNAUTHENTICATED.withDescription("API Key is required"),
				new Metadata()
			);
			return new ServerCall.Listener<>() {};  // 빈 Listener 반환 (요청 거부)
		}

		// 3. API Key 검증
		if (!validApiKey.equals(apiKey)) {
			log.warn("[gRPC] Invalid API Key - Method: {}, Provided: {}", method, apiKey);
			call.close(
				Status.UNAUTHENTICATED.withDescription("Invalid API Key"),
				new Metadata()
			);
			return new ServerCall.Listener<>() {};
		}

		// 4. 검증 성공 - 다음 핸들러(Controller)로 진행
		log.debug("[gRPC] API Key validated - Method: {}", method);
		return next.startCall(call, headers);
	}
}