# 프로젝트 작업 지침

# Enum · 스키마 변경

MySQL native ENUM/CHECK 는 `ddl-auto=update` 가 값을 추가하지 않는다. Java enum 상수만 늘리면 INSERT 가 500/1004 가 난다 (#110 → #167).

- `@Enumerated(EnumType.STRING)` 은 VARCHAR 로 저장한다. `hibernate.type.preferred_enum_jdbc_type=VARCHAR` 가 전역 고정이다. 컬럼을 MySQL `ENUM(...)` 으로 되돌리지 않는다.
- **enum 상수 추가**(예: `ESTATE`)는 Java enum·검증·관계 전략만 바꾼다. `ALTER TABLE` / `deploy/sql` 수동 실행을 요구하지 않는다.
- Hibernate 가 안 바꾸는 DDL(NOT NULL→NULL, 컬럼 타입, 인덱스)은 같은 PR 에서 `MysqlSchemaCompatibilityMigrator` 에 멱등 스텝을 넣는다. RDS 에 수동 SQL 을 전제로 하지 않는다.
- 스키마 보정 실패는 기동을 실패시킨다. `ApplicationRunner` 에서 예외를 삼키지 않는다.

- ❌ BAD: `ALTER TABLE ... ENUM('SOCIAL',...,'ESTATE')` 를 배포 전제로 두거나, `ddl-auto=update` 가 ENUM 값을 갱신한다고 가정한다.
- ✅ GOOD: Java enum·검증·관계 전략만 추가한다. 기존 DB ENUM/CHECK 는 `MysqlSchemaCompatibilityMigrator` 가 VARCHAR 로 정규화한다.

# QA 회귀 문서 갱신

이슈·버그·계약 변경을 **코드에 반영한 뒤** `docs/QA-회귀-체크리스트.md`가 있으면 같은 작업에서 항목을 추가한다. 파일이 없으면 만들지 말고 건너뛴다. 이 파일은 gitignore 대상이라 커밋하지 않는다.

## 언제

- GitHub 이슈(`#N`)를 고치거나, 이슈 없이 사용자-facing 동작을 바꾼 직후
- 인프라/리팩터만이고 QA가 확인할 동작이 없으면 건너뛴다

## 어떻게

기존 도메인 섹션(인증·수신자·애프터노트·마음기록·업로드·공통 오류)에 이어서 번호를 붙인다. 새 도메인이면 섹션을 만든다.

필수 칸: **우선순위(P0/P1/P2)** · 이슈 링크 · **재현** · **합격** (HTTP·code·필드). 부록 C에 새 오류 코드, 부록 D에 이슈→PR을 한 줄 추가한다. 상단 기준일은 오늘로 바꾼다.

부록 A에 있던 “닫혔지만 미반영”을 이번에 고쳤으면 본문으로 옮기고 부록 A에서 뺀다. 부록 B 열린 이슈를 고쳤으면 부록 B에서 뺀다.

```markdown
### 2-6. [P1] 짧은 제목 — [#167](https://github.com/Afternote/Afternote-BE/issues/167)

**재현**

1. …

**합격:** HTTP 400, `code 16xx`. 500/`1004`이면 실패.
```
