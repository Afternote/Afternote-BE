# AfterNote PM 계정 운영 인프라

현재 운영 Terraform 루트입니다. AWS 프로필은 `afternote-pm`, 허용 계정은
`079477842373`, 리전은 서울(`ap-northeast-2`)로 고정합니다.
이전 계정의 `../terraform`과 state를 섞지 않습니다.

2026-11-22 이전과 구 계정의 AfterNote 리소스 정리를 완료했습니다.
운영 리소스 51개와 state 버킷 설정 6개를 관리하며, 이전 계정의 `clog`와 개인 IAM 사용자는 보존했습니다.
배포·스모크·계약 테스트는 [GitHub Actions 실행 결과](https://github.com/Afternote/Afternote-BE/actions/runs/44992845669)에서 확인할 수 있습니다.
구 리소스 삭제 후에도 HTTPS, DB/Redis/SMTP 연결, 로그인, 오늘의 질문 API를 검증했습니다.

## 운영 명령

Terraform 1.10 이상에서 저장소 루트 기준으로 실행합니다.
`afternote-pm` 프로필과 Git에서 제외된 운영 `terraform.tfvars`를 준비합니다.

```bash
aws sts get-caller-identity --profile afternote-pm
terraform -chdir=infra/terraform-pm init
terraform -chdir=infra/terraform-pm fmt -check
terraform -chdir=infra/terraform-pm validate
terraform -chdir=infra/terraform-pm plan
```

`init`은 PM 계정의 비공개 S3에서 기존 운영 state를 읽습니다.
새 PC에서는 로컬 state를 복사하지 않습니다. 운영 변수 파일은 별도로 안전하게 전달합니다.

## S3 원격 state

- 버킷: `afternote-terraform-state-079477842373-ap-northeast-2`
- state 키: `prod/terraform.tfstate`
- 잠금 키: `prod/terraform.tfstate.tflock` (`use_lockfile = true`)
- PM 계정만 허용하며, 공개 접근 차단·버킷 소유자 강제·AES256 암호화·버전 관리·HTTPS 강제를 적용합니다.
- state 버킷도 이 루트에서 관리하며 `prevent_destroy = true`로 실수에 의한 삭제를 막습니다.
- 2026-11-23 현재 PM state를 이전했습니다. 이전 계정의 폐기된 state는 원격 backend에 넣지 않았습니다.

운영자는 기존 인프라 권한 외에 state 버킷의 `s3:ListBucket`, state 객체의
`s3:GetObject`/`s3:PutObject`, 잠금 객체의 `s3:GetObject`/`s3:PutObject`/`s3:DeleteObject`
권한이 필요합니다. 앱의 업로드 전용 IAM 사용자는 state 버킷 권한을 갖지 않습니다.
동시에 실행된 Terraform 작업은 잠금으로 차단됩니다. 실행 중인 작업이 있다면
잠금 객체를 직접 지우거나 `force-unlock`으로 우회하지 않고 해당 작업이 끝나기를 기다립니다.

이미 운영 중인 환경에서는 `terraform init`만 실행합니다.
버킷을 다시 만들거나 `-migrate-state`/`-force-copy`로 오래된 로컬 state를 덮어쓰지 않습니다.
이전 전후 백업은 Git에서 제외된 `.state-migration-backups/20261123/`에 보관합니다.

완전한 재해 복구로 버킷부터 다시 만드는 경우에는 먼저 `backend.tf`를 제외한 임시 루트에서
버킷을 준비한 뒤 올바른 최신 state를 복원하고 backend를 연결해야 합니다.
일반적인 clone과 운영 작업에서는 이 부트스트랩 절차가 필요하지 않습니다.

## 구성과 배포

- VPC, 서브넷, EC2(`t3.small`), EIP, 비공개 MySQL 8.4 RDS(`db.t4g.micro`).
- 비공개 S3, CloudFront OAC, 업로드 버킷에만 접근하는 앱 전용 IAM 사용자.
- EC2 SSM 역할, CloudWatch 로그·알람·대시보드, SNS 알림.
- 한국시간 03:00 중지 / 12:00 시작 스케줄. 해당 시간 동안 서비스는 중단됩니다.
- RDS 삭제 방지와 최종 스냅샷을 활성화합니다.
- `deploy-key.pub`로 SSH 키페어를 관리합니다. 개인 키 `deploy-key`는 Git에서 제외합니다.
- EC2 부팅 시 `afternote-compose` 서비스가 `/home/ec2-user/deploy`의 Compose를 기동합니다.
- Amazon Linux의 기존 `curl-minimal`과 충돌하지 않도록 Docker와 curl을 따로 처리합니다.

GitHub Actions의 `EC2_HOST`, `EC2_USER`, `EC2_KEY`, `DEPLOY_PRODUCTION_ENV`가
이 서버를 대상으로 합니다. 앱 환경설정 원본은 Git에서 제외된
`deploy/production.env`이며, EC2에서는 `~/deploy/.env`로 사용합니다.
`APP_BASE_URL`은 `https://afternote.kro.kr`입니다.
DockerHub 이미지, 메일, Firebase 등 외부 서비스는 기존 설정을 재사용합니다.

```bash
terraform -chdir=infra/terraform-pm output elastic_ip
terraform -chdir=infra/terraform-pm output jdbc_url_hint
terraform -chdir=infra/terraform-pm output uploads_base_url
```

## 필수 초기 데이터

스키마는 앱의 JPA 설정으로 생성되지만 공통 질문은 자동 생성되지 않습니다.
2026-11-22 이전 시 `daily_question`의 질문 100개와 활성 상태를 복사했습니다.
사용자 정보, 질문 답변과 기존 업로드 파일은 이전하지 않았습니다.
새 DB를 다시 만들 때는 공통 질문을 복원하고 `/api/v1/daily-questions/today`가
로그인 후 200과 질문 내용을 반환하는지 확인해야 합니다.

배포 계약 테스트에는 일반 권한의 전용 계정이 필요합니다.
새 DB에서는 이 계정도 준비하고 GitHub의 `CONTRACT_TEST_EMAIL`과
`CONTRACT_TEST_PASSWORD`를 일치시켜야 합니다.

## 보관할 로컬 파일

`terraform.tfstate`와 백업, `terraform.tfvars`, `deploy-key`, 운영 env,
저장된 plan에는 인증정보가 포함될 수 있습니다. Git에서 제외한 상태로 안전하게 보관합니다.
잠금 파일 `.terraform.lock.hcl`과 공개 키 `deploy-key.pub`는 버전 관리합니다.

provider 잠금 파일을 갱신할 때는 Linux CI와 macOS의 체크섬을 함께 기록합니다.
CI는 `-lockfile=readonly`로 실행하므로 누락된 플랫폼 해시를 자동으로 보완하지 않습니다.

```bash
terraform -chdir=infra/terraform-pm providers lock -platform=linux_amd64 -platform=darwin_arm64
```

완전히 새 환경을 생성할 때만 `terraform.tfvars.example`을 복사해 값을 채우고,
새 SSH 개인 키와 공개 키를 준비합니다. 기존 운영 환경에서는 키나 원격 state를
덮어쓰지 않습니다. `ssh_ingress_cidr`에는 실제 SSH 배포 주체가 접근할 수 있는
범위가 필요하며 GitHub Actions의 SSH 배포도 이 규칙의 적용을 받습니다.
