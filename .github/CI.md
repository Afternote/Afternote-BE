# 자동 검증 범위

| 구성 | 실행 시점 | 책임 |
| --- | --- | --- |
| `ci.yml` | `main` 대상 PR·push | PR dependency graph 제출 후 review, 일반 JUnit, MySQL 통합 테스트, 현재 `Dockerfile` 이미지의 기동·HTTP baseline을 검증하고 `CI / build`에서 집계 |
| `quality.yml` | `main` 대상 PR·push, 수동 실행 | Actionlint, ShellCheck, 미해결 merge marker 검사 |
| `codeql.yml` | `main` 대상 PR·push, 주 1회, 수동 실행 | Java CodeQL 보안 분석 |
| `dependency-submission.yml` | `main` push, 수동 실행 | Gradle 의존성 그래프 제출 |
| `deploy.yml` | `main` push, 수동 실행 | 기존 DockerHub·EC2·Nginx/TLS·smoke·contract 배포 흐름 |
| `tls-expiry.yml` | 매일 22:00 UTC, 수동 실행 | 공개 HTTPS 인증서 잔여 일수가 21일 이하이면 실패 (#188) |
| `dependabot.yml` | 매주 월요일 | Gradle, GitHub Actions, Docker 업데이트 PR을 `main` 대상으로 생성 |

기본 PR 필수 검사는 이름이 고정된 `CI / build`로 설정한다. 동일 저장소 PR에서는 `contents: write`를 해당 job에만 부여해 `dependency-submission`을 완료한 뒤 `dependency-review`를 실행하고, snapshot 반영을 최대 10분 기다린다. 외부 fork PR에는 write 토큰을 부여하지 않는다. `gradle-test`, `mysql-test`, `docker-image`는 이 흐름과 병렬로 실행하며, 집계 job은 네 검증 결과를 모두 요구한다. `main` push에서는 이벤트 특성상 실행되지 않는 dependency review만 `skipped`로 허용하고 나머지 실패·취소는 실패로 처리한다.

JUnit은 CI에서 두 job으로 나눈다. `gradle-test`의 `standardTest`는 `*MySqlTest`를 제외한 테스트를 실행하고, `mysql-test`는 Testcontainers가 제공하는 실제 MySQL 8.0에서 `*MySqlTest`만 실행한다. `mysql-test`는 대상 테스트가 0개이거나 하나라도 skip되면 실패한다. 로컬의 기존 `test` task는 전체 테스트를 실행하는 동작을 유지한다.

`docker-image`는 이미지를 build한 뒤 GitHub Actions의 임시 MySQL·Redis에 연결해 실제 컨테이너를 기동한다. 이어서 `/v3/api-docs`의 Time-Letters 경로, `/actuator/health`의 MySQL·Redis 상태, `/.well-known/assetlinks.json`, `/api/v1/app/version` 응답을 직접 확인한다. 이 단계에는 repository secret이나 외부 서비스 호출이 필요하지 않다.

정적 검사와 CodeQL은 각 전용 workflow에서 실행한다. 기존 `deploy.yml`은 이 작업에서 변경하지 않으며, 같은 검사를 배포 workflow에 추가해 반복하지 않는다.

## 테스트 실패 확인

`gradle-test`가 실패하면 Gradle 실행 로그에서 실패한 테스트명과 예외를 먼저 확인한다. 전체 테스트 리포트는 실패한 job의 `gradle-test-report` artifact에서 확인한다. `mysql-test-report` artifact는 성공 여부와 관계없이 업로드하므로, 실제 실행 수와 skip 수를 XML·HTML 리포트에서 확인할 수 있다. Gradle 명령을 별도 성공 처리 단계로 감싸지 않으므로 테스트 종료 코드가 그대로 job 결과에 반영된다.

`docker-image`의 HTTP baseline이 실패하면 step 로그의 마지막 애플리케이션 로그와 `http-smoke-log` artifact를 확인한다. 검증 스크립트는 애플리케이션의 HTTP 준비 상태를 기본 60회 재시도한 뒤 실패한다.

## PR 전 테스트 누락 점검

CI는 작성하지 않은 테스트를 발견할 수 없다. PR CI를 실행하기 전에 변경 diff와 영향 경계를 대조해 정상·실패·경계·회귀 테스트 누락을 점검하고, 필요한 테스트를 보완한다. CI가 같은 입력·경계·결과를 이미 단언하는 경우에는 로컬이나 직접 실측으로 반복하지 않는다.

## 미확인 경계

합의되지 않았거나 아직 존재하지 않는 job은 성공 처리용 `skip` 또는 빈 placeholder로 만들지 않는다. PR-local HTTP baseline은 실제 `Dockerfile`과 임시 MySQL·Redis의 통합만 확인한다. 인증 계정이 필요한 전체 API 실측, 실제 EC2·RDS, Sentry 전송, 외부 API, 부하·race 검증을 기본 CI 통과로 검증됐다고 간주하지 않는다.

`CI / build`에 어떤 검증을 언제 연결할지는 #198이 관리한다. 대상 목록과 보류 사유는 이 문서에 복사하지 않는다.
