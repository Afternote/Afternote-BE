# 애프터노트 작성자 상세 계약 (#288)

대상: `GET /api/v1/afternotes/{afternoteId}`. 수신자 상세(#45)와 생성/PATCH 입력 계약은 별도다.

JSON 키, 중첩 구조, 기존 null 값과 발행 검증은 유지한다. 런타임 DTO를 늘리는 대신 실제 생성 OpenAPI에 명시적인 종류·상태 스키마를 제공한다. SOCIAL/BUSINESS는 같은 계약을 공유하므로 네 종류 × 두 상태를 여섯 스키마로 표현한다.

| category | isDraft | 스키마 | credentials | playlist |
| --- | --- | --- | --- | --- |
| SOCIAL / BUSINESS | true | AfternoteDraftAccountDetailResponse | null 또는 부분 작성 객체 | null |
| SOCIAL / BUSINESS | false | AfternotePublishedAccountDetailResponse | 필수. id/password도 non-null 필수 | null |
| GALLERY | true | AfternoteDraftGalleryDetailResponse | null | null |
| GALLERY | false | AfternotePublishedGalleryDetailResponse | null | null |
| PLAYLIST | true | AfternoteDraftPlaylistDetailResponse | null | null 또는 미작성 값/빈 곡 목록을 가진 객체 |
| PLAYLIST | false | AfternotePublishedPlaylistDetailResponse | null | 필수. songs는 non-null이며 최소 1곡 |

공통 필드: afternoteId, category, title, isDraft, actions, leaveMessage, credentials, receivers, playlist, updatedAt. PLAYLIST의 actions는 null이다. 종류에 적용되지 않는 필드는 삭제하지 않고 기존처럼 null로 내려준다.

`category`와 `isDraft`의 허용값으로 `oneOf`가 하나의 응답만 선택한다. `category` 하나로는 임시저장과 발행을 구분할 수 없어 discriminator를 category에만 걸지 않는다. 기존 AfternoteDraftDetailResponse / AfternotePublishedDetailResponse 이름은 해당 하위 타입들의 oneOf 별칭으로 남는다. OpenAPI를 이용해 클라이언트 코드를 생성하는 경우 새 스키마 구성을 반영해 재생성해야 한다. 일반 JSON 소비 클라이언트의 키/값 변경은 없다.

임시저장 credentials/playlist와 선택 영상은 객체 참조 또는 null만 허용하는 anyOf다. 적용되지 않는 데이터는 nullable + enum [null]로 표현한다. 입력용 DTO의 PATCH 삭제 설명과 발행 검증을 응답 스키마로 복사하지 않도록 응답용 credentials/playlist/video 스키마를 둔다.

updatedAt은 실제 응답에 맞춰 오프셋 없는 ISO-8601 문자열이며 OpenAPI format은 local-date-time이다. 응답에 없던 UTC 오프셋을 추가하지 않는다.

## 응답 예시 (data 내부)

발행 계정형:

```json
{"afternoteId":1,"category":"SOCIAL","title":"계정 정리","isDraft":false,"actions":["계정 삭제"],"leaveMessage":[],"credentials":{"id":"example-id","password":"example-password"},"receivers":[],"playlist":null,"updatedAt":"2026-10-29T12:00:00"}
```

부분 작성 계정형의 credentials는 `{"id":"example-id","password":null}`일 수 있다. PLAYLIST 임시저장은 playlist 자체가 null일 수 있고, 객체가 있으면 songs가 빈 배열일 수 있다. 발행 PLAYLIST의 예시는 기존 키를 유지하며 `playlist.songs`에 한 곡 이상이 포함된다.

## 검증

```sh
./gradlew standardTest --tests 'com.afternote.global.config.OpenApiGeneratedDocsTest'
python3 -m venv /tmp/afternote-contract-validation
/tmp/afternote-contract-validation/bin/python -m pip install -r scripts/afternote-contract-requirements.txt
/tmp/afternote-contract-validation/bin/python scripts/validate-afternote-detail-contract.py
```

실제 생성 `/v3/api-docs`와 MockMvc HTTP 응답 11개를 검증한다. category/isDraft 누락, 미지원 category, 발행 credentials/id/password 누락·null·빈 문자열, 발행 playlist/songs 누락·null·빈 배열, 다른 종류의 전용 데이터 삽입은 거부되는지 확인한다. 실제 서비스의 발행 필수값 검증은 기존 AfternoteServiceTest/AfternoteValidatorTest에서 함께 확인한다.

FE 연계: https://github.com/Afternote/Afternote-FE/issues/1959
