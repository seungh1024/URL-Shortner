## 구현 내용
URL 단축 서비스 핵심 기능 정리 및 구조 재설계
- 생성: 원본 URL을 8자리 Base62 short_code로 단축
- 조회: short_code로 원본 URL 리다이렉션
- 삭제: short_code로 삭제
- gRPC 내부 API와 REST 외부 API 모두 정합성 유지
- URL 입력 검증 추가

## 구현 방식

### 1. URL 생성 플로우 (수정된 최종 설계)
```
원본 URL 입력
  -> URL 유효성 검증 (http/https, host 존재, length <= 2048)
  -> SHA-256 해시 생성 (hash_key)
  -> hash_key 기반 named lock 획득
  -> hash_key 조회 (expired 제외)
  -> 동일 URL 존재 시 기존 short_code 반환
  -> 없으면 short_code 생성 (Base62 랜덤 8자리)
  -> short_code UNIQUE 충돌 시 최대 3회 재시도
  -> 저장 (만료일: 7일 후)
```

### 2. gRPC/REST API (성능 비교 목적)
- gRPC
  - CreateLink: short_code + short_url 반환
  - DeleteLink: short_code 삭제
  - API Key 인증은 Interceptor에서 처리
- REST
  - POST /link: 단축 생성 (201)
  - GET /link/{key}: 리다이렉션

### 4. 에러 처리
- 존재하지 않는 키: 404 NOT_FOUND
- 만료된 링크: 404 NOT_FOUND
- 잘못된 키 형식: 400 BAD_REQUEST
- URL 스킴/호스트/길이 불일치: 400 BAD_REQUEST
- short_code 충돌 3회 초과: 500 INTERNAL_SERVER_ERROR

## 주요 설계 변경 및 해결

### 1. URL 고유성/동시성 처리 재설계
- 이전 시도: 원본 URL에 UNIQUE 인덱스를 두려 했으나, URL이 길어 인덱스 제약/비용 문제 발생
- 대안: URL을 SHA-256으로 해시해 `hash_key`로 사용
- 문제: 해시 충돌이 동일/상이 URL 모두에서 발생 가능하므로 `hash_key`에 UNIQUE를 걸면 정상 URL도 삽입 실패
- 결정: `hash_key`는 **NON-UNIQUE + INDEX**로 유지
- 해결: named_lock으로 동일 hash_key 요청을 직렬화하고, 조회 결과에서 **redirection_url 문자열 비교**로 동일 URL이면 기존 short_code를 반환하여 중복 생성 방지

### 2. hash_key/short_code 역할 분리
- 기존: hash_key가 사실상 short_code 역할을 겸함 (길이 8 + UNIQUE)
- 변경: hash_key = SHA-256 조회/락 키, short_code = 8자리 Base62 리다이렉션 키(UNIQUE)

### 3. API 명세 정리 및 입력 검증 추가 (단순 수정)
- gRPC 응답 필드명을 short_code로 정리 (의미 혼동 제거)
- URL 입력 검증 추가 (http/https, host 존재, 길이 2048)

### 4. 동시성 버그 수정 (named lock 해제 시점)
- 문제: 락을 `finally`에서 해제해 **커밋 전에 락이 풀릴 수 있었고**, 동일 URL 동시 요청 시 중복 생성 가능
- 해결: `TransactionSynchronization.afterCompletion`에서 락 해제하도록 변경해 **커밋/롤백 이후에만 락 해제**
- 검증: 동일 URL에 대해 **멀티스레드 동시 생성 통합 테스트** 추가

## 기술적 의사결정 요약

### 1. hash_key + short_code 분리
- hash_key: SHA-256, NON-UNIQUE, URL 조회용 키 + 락 키
- short_code: Base62 8자리, UNIQUE, 리다이렉션 키

### 2. named lock 도입
- 같은 hash_key 요청만 직렬화
- hash_key 충돌은 URL 문자열 비교로 방어

### 3. Base62 랜덤 short_code
- URL-Safe
- 8자리 = 62^8 경우의 수
- 충돌 시 최대 3회 재시도

### 4. URL 길이 제한
- max 2048자
- 서비스/DTO 레벨 모두 적용

## 테스트
- ./gradlew test

## 기타
- REST create는 @ResponseStatus 기반으로 단순화
- Controller 테스트는 WebMvcTest(boot4 패키지) + MockitoBean 사용
