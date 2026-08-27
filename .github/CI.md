# 자동 검증 범위

| 구성 | 실행 시점 | 책임 |
| --- | --- | --- |
| `ci.yml` | `main` 대상 PR·push | PR dependency graph 제출 후 review, 일반 JUnit, 현재 `Dockerfile` 이미지 build를 검증하고 `CI / build`에서 집계 |
| `quality.yml` | `main` 대상 PR·push, 수동 실행 | Actionlint, ShellCheck, 미해결 merge marker 검사 |
| `codeql.yml` | `main` 대상 PR·push, 주 1회, 수동 실행 | Java CodeQL 보안 분석 |
| `dependency-submission.yml` | `main` push, 수동 실행 | Gradle 의존성 그래프 제출 |
| `deploy.yml` | `main` push, 수동 실행 | 기존 DockerHub·EC2·Nginx/TLS·smoke·contract 배포 흐름 |
| `tls-expiry.yml` | 매일 22:00 UTC, 수동 실행 | 공개 HTTPS 인증서 잔여 일수가 21일 이하이면 실패 (#188) |
| `dependabot.yml` | 매주 월요일 | Gradle, GitHub Actions, Docker 업데이트 PR을 `main` 대상으로 생성 |

기본 PR 필수 검사는 이름이 고정된 `CI / build`로 설정한다. 동일 저장소 PR에서는 `contents: write`를 해당 job에만 부여해 `dependency-submission`을 완료한 뒤 `dependency-review`를 실행하고, snapshot 반영을 최대 10분 기다린다. 외부 fork PR에는 write 토큰을 부여하지 않는다. `gradle-test`와 `docker-image`는 이 흐름과 병렬로 실행하며, 집계 job은 세 검증 결과를 모두 요구한다. `main` push에서는 이벤트 특성상 실행되지 않는 dependency review만 `skipped`로 허용하고 나머지 실패·취소는 실패로 처리한다.

정적 검사와 CodeQL은 각 전용 workflow에서 실행한다. 기존 `deploy.yml`은 이 작업에서 변경하지 않으며, 같은 검사를 배포 workflow에 추가해 반복하지 않는다.

## 테스트 실패 확인

`gradle-test`가 실패하면 Gradle 실행 로그에서 실패한 테스트명과 예외를 먼저 확인한다. 전체 테스트 리포트는 실패한 job의 `gradle-test-report` artifact에서 확인한다. Gradle 명령을 별도 성공 처리 단계로 감싸지 않으므로 테스트 종료 코드가 그대로 job 결과에 반영된다.

## PR 전 테스트 누락 점검

CI는 작성하지 않은 테스트를 발견할 수 없다. PR CI를 실행하기 전에 변경 diff와 영향 경계를 대조해 정상·실패·경계·회귀 테스트 누락을 점검하고, 필요한 테스트를 보완한다. CI가 같은 입력·경계·결과를 이미 단언하는 경우에는 로컬이나 직접 실측으로 반복하지 않는다.

## 미확인 경계

합의되지 않았거나 아직 존재하지 않는 job은 성공 처리용 `skip` 또는 빈 placeholder로 만들지 않는다. 현재 PR CI는 실제 EC2·RDS·Sentry 전송·외부 API를 재현하지 않으며, 이를 기본 CI 통과로 검증됐다고 간주하지 않는다.

`CI / build`에 어떤 검증을 언제 연결할지는 #198이 관리한다. 대상 목록과 보류 사유는 이 문서에 복사하지 않는다.
