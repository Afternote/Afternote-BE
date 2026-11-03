# 이번 작업의 Swagger 계약 검증

## 범위

| 작업 | 확인 결과 |
| --- | --- |
| #288 작성자 애프터노트 상세 | 카테고리·발행 여부 8조합과 부분 작성 3개 응답을 실제 생성 OpenAPI로 검증한다. |
| #45 수신자 애프터노트 상세 | 4개 카테고리, 계정 정보 유무, playlist·추모 영상의 null/객체 응답을 검증한다. |
| #190 타임레터 수신자 등록 | 200의 연결 ID 배열과 409의 오류 본문을 각각 검증한다. |
| #96 승인 시각 | 생성 Swagger에서 createdAt 필수, approvedAt nullable·로컬 시각 형식을 확인한다. |
| #304 푸시 토큰 무효화 | 정상/무효 토큰 HTTP 응답과 무효 시각·사유·requiresReset 필드를 검증한다. |
| #272 Android 패스키 origin | 신뢰 origin의 서버 설정 변경으로 요청·응답 구조 변경이 없다. |
| #298 배포·TLS 워크플로 Actions SHA 고정 | API 요청·응답 변경이 없어 Swagger 변경 대상이 아니다. |

#190의 409 응답은 springdoc가 메서드의 성공 반환형을 재사용하여 `data`를 숫자 배열로 생성하고 있었다. 실제 공통 오류 응답은 `status: 409`, `code: 1909`, `data: null`이다. 전용 `TimeLetterReceiverConflictResponse`로 보정하고 필수 필드 및 잘못된 값 거부를 검증한다.

#304의 `lastSeenAt`·`invalidatedAt`은 실제 오프셋 없는 LocalDateTime 문자열과 달리 `date-time`으로 문서화되어 있었다. 기존 로컬 시각 보정 규칙을 적용하여 `local-date-time`으로 맞추고 정상/무효 토큰 HTTP 응답을 검증한다.

수신자 상세의 playlist와 추모 영상은 참조 대상 스키마에도 nullable이 남아 있어 null 응답이 OpenAPI 3.0 검증을 통과한다. 계정 정보의 anyOf 객체/null 표현도 통과한다.

## 재현

```sh
./gradlew standardTest
python3 -m venv /tmp/afternote-swagger-validation
/tmp/afternote-swagger-validation/bin/python -m pip install -r scripts/afternote-contract-requirements.txt
/tmp/afternote-swagger-validation/bin/python scripts/validate-afternote-detail-contract.py
/tmp/afternote-swagger-validation/bin/python scripts/validate-receiver-contract.py
/tmp/afternote-swagger-validation/bin/python scripts/validate-receiver-contract.py build/reports/push-token-contract
```

JUnit은 MockMvc로 컨트롤러를 호출하여 직렬화된 HTTP 응답과 `/v3/api-docs` 산출물을 내보낸다. 서비스는 고정된 합성 데이터를 반환하도록 대체한다. Python 검증기는 HTTP 상태별 OpenAPI 스키마 참조를 따라 응답을 검사한다. 실제 권한 검사·복호화·중복 등록·동시성은 별도의 서비스 및 DB 테스트에서 검증한다. 운영 사용자의 비밀번호나 기록을 조회하지 않는다.
