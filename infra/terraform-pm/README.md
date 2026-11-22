# AfterNote PM 계정 운영 인프라

현재 운영 Terraform 루트입니다. AWS 프로필은 `afternote-pm`, 허용 계정은
`079477842373`, 리전은 서울(`ap-northeast-2`)로 고정합니다.
이전 계정의 `../terraform`과 state를 섞지 않습니다.

2026-11-22 이전과 구 계정의 AfterNote 리소스 정리를 완료했습니다.
PM 관리 리소스는 51개이며, 이전 계정의 `clog`와 개인 IAM 사용자는 보존했습니다.
배포·스모크·계약 테스트는 [GitHub Actions 실행 결과](https://github.com/Afternote/Afternote-BE/actions/runs/44992845669)에서 확인할 수 있습니다.
구 리소스 삭제 후에도 HTTPS, DB/Redis/SMTP 연결, 로그인, 오늘의 질문 API를 검증했습니다.

## 운영 명령

저장소 루트에서 실행합니다. 기존 환경을 관리할 때는 운영 중인 state와
`terraform.tfvars`를 보관한 디렉터리를 사용합니다.

```bash
aws sts get-caller-identity --profile afternote-pm
terraform -chdir=infra/terraform-pm init
terraform -chdir=infra/terraform-pm fmt -check
terraform -chdir=infra/terraform-pm validate
terraform -chdir=infra/terraform-pm plan
```

새로 clone한 뒤 빈 state로 apply하면 기존 리소스를 자동으로 인계하지 않습니다.
현재 backend는 로컬이므로 작업 PC를 옮기기 전에 state와 변수 파일을 안전하게
이관하거나 별도 작업으로 원격 backend를 구성해야 합니다.

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

완전히 새 환경을 생성할 때만 `terraform.tfvars.example`을 복사해 값을 채우고,
새 SSH 개인 키와 공개 키를 준비합니다. 기존 운영 환경에서는 키나 state를
덮어쓰지 않습니다. `ssh_ingress_cidr`에는 실제 SSH 배포 주체가 접근할 수 있는
범위가 필요하며 GitHub Actions의 SSH 배포도 이 규칙의 적용을 받습니다.
