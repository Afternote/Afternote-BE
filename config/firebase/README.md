# Firebase 설정 (AfterNote)

## 파일 구분

| 파일 | 용도 | 위치 |
| --- | --- | --- |
| `google-services.json` | **Android 앱(FE)** — Firebase SDK 초기화 | FE `android/app/google-services.json` |
| 서비스 계정 JSON | **서버(BE)** — FCM 발송 (Admin SDK) | env `FIREBASE_SERVICE_ACCOUNT_JSON` (git 금지) |

`google-services.json`만으로는 서버에서 푸시를 **보낼 수 없습니다**. 콘솔 → 프로젝트 설정 → 서비스 계정 → **새 비공개 키 생성** JSON이 추가로 필요합니다.

## 이 프로젝트 (afternote-7471f)

- **project_id:** `afternote-7471f`
- **Android package:** `com.afternote.app`

로컬 참고용 `google-services.json`은 이 폴더에 두고 git에는 올리지 않습니다 (`.gitignore`).

## 서버 env

```bash
FIREBASE_PROJECT_ID=afternote-7471f
FIREBASE_ANDROID_PACKAGE_NAME=com.afternote.app
# 서비스 계정 JSON 전체를 한 줄(또는 이스케이프)로
FIREBASE_SERVICE_ACCOUNT_JSON={"type":"service_account",...}
```

`FIREBASE_SERVICE_ACCOUNT_JSON`이 비어 있으면 토큰 등록 API는 동작하고, 실제 FCM 발송만 비활성(no-op)됩니다.
