# AfterNote 그린필드 인프라 (Terraform)

VPC / EC2 / RDS / EIP / EventBridge Scheduler(Stop·Start) / SNS 알람을 코드로 생성합니다.

- **Stop** 01:00 KST · **Start** 12:00 KST
- EC2 부팅 시 `afternote-compose` systemd가 `~/deploy` compose를 기동
- Start Lambda는 RDS available 대기 + EC2 start + (백업) SSM compose

타임스탬프(`created_at`/`updated_at`)는 Spring Data JPA Auditing이 관리합니다. RDS는 **빈 DB**로 두고 앱 배포로 스키마를 올립니다.

---

## 당신이 채워야 하는 것

### 1. `terraform.tfvars` (git 금지)

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
```

| 변수 | 설명 |
|------|------|
| `ec2_key_name` | 리전에 이미 있는 EC2 키페어 이름 |
| `ssh_ingress_cidr` | SSH 허용 CIDR (가능하면 `내IP/32`) |
| `db_password` | RDS 마스터 비밀번호 |
| `alert_email` | SNS 알림 메일 |
| `existing_eip_allocation_id` | 기존 EIP 재사용 시 `eipalloc-...`, 아니면 `""` |

### 2. 로컬 AWS 자격 증명

```bash
aws configure
```

필요 권한: EC2, VPC, RDS, Lambda, IAM, EventBridge Scheduler, SNS, CloudWatch, EIP.

### 3. apply 후 한 번 설정

| 어디 | 무엇 |
|------|------|
| DNS A 레코드 | `terraform output elastic_ip` |
| GitHub `EC2_HOST` | 동일 Elastic IP |
| EC2 `~/deploy/.env.production` | `terraform output jdbc_url_hint` + `db_password` 등 |
| SNS | `alert_email` Confirm subscription |
| GitHub Actions | `main` push로 첫 배포 (compose·이미지 동기화) |

S3 버킷·메일 SMTP·도메인 자체는 재사용합니다 (일단계 비범위).

---

## 배포 순서 (그린필드)

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
# terraform.tfvars 편집

terraform init
terraform plan
terraform apply

terraform output elastic_ip
terraform output jdbc_url_hint
```

1. DNS A + GitHub `EC2_HOST`를 새 EIP로 변경  
2. SSH로 EC2에 `.env.production` 작성 (`jdbc_url_hint` 사용)  
3. `main` 배포 또는 `deploy/` compose를 EC2에 동기화  
4. Let's Encrypt / certbot 최초 발급 (기존 절차)  
5. 스모크: `/v3/api-docs`, `/api/v1/app/version`  
6. 스케줄 수동 검증:

```bash
aws lambda invoke --function-name afternote-stop-env /tmp/afternote-stop.json
# EC2/RDS stopped 확인

aws lambda invoke --function-name afternote-start-env /tmp/afternote-start.json
# RDS available, EC2 running, docker compose, API 확인
```

---

## 구 리소스 폐기 (신 스택 검증 후에만)

**지금 바로 전부 지우지 마세요.** 신 스택·DNS·배포가 안정된 뒤:

| 삭제 OK | 유지 |
|---------|------|
| 구 EC2 / 구 RDS | S3 버킷 |
| 앱 전용 구 VPC·SG | 도메인 (A만 교체) |
| DNS 이전 후 구 EIP | GitHub Secrets 이름 (값만 갱신) |
| TF 밖 중복 스케줄 | 메일 SMTP, S3 자격 증명 |

기존에 이 디렉터리로 apply했던 **스케줄러 Lambda/SNS**는 콘솔에서 손으로 지우지 말고, 같은 state로 plan/apply 하며 새 EC2/RDS에 연결되게 맞춥니다.  
구 콘솔 EC2/RDS는 Terraform 밖이면 콘솔/`aws` CLI로 terminate/delete 합니다.

---

## 스케줄

| 동작 | 시간 (KST) | cron |
|------|------------|------|
| Stop | 01:00 | `cron(0 1 * * ? *)` |
| Start | 12:00 | `cron(0 12 * * ? *)` |

---

## 모니터링

| 리소스 | 위치 |
|--------|------|
| CloudWatch Dashboard | `afternote-scheduler` |
| Stop Lambda 로그 | `/aws/lambda/afternote-stop-env` |
| Start Lambda 로그 | `/aws/lambda/afternote-start-env` |
| 알림 | SNS → `alert_email` |

---

## 주의사항

- **01:00~12:00 KST** 서비스 다운
- **off-hours 중 `main` push** → EC2 stopped면 deploy 실패 → Start Lambda 먼저 invoke
- **RDS Stop 7일** → AWS가 자동 Start할 수 있음
- **Elastic IP** stopped 시간에도 Public IPv4 과금
- RDS는 VPC 내부만 (`publicly_accessible = false`). 로컬에서 DB 직접 접속은 불가(EC2 경유)

---

## 산출물 요약

```text
vpc + 2 public subnets
ec2 (AL2023, docker, compose systemd) + EIP
rds mysql 8 (EC2 SG only)
eventbridge scheduler → stop/start lambda
sns + cloudwatch alarms/dashboard (afternote-ops)
  - EC2: StatusCheckFailed, CPU>80%
  - RDS: FreeStorage<2GiB, Connections>40, CPU>80%
  - Lambda: stop/start Errors
  - off-hours stop: missing metrics = notBreaching (no false alarm)
```

대시보드: `terraform output cloudwatch_dashboard_url`  
알람 메일: `alert_email` SNS 구독 Confirm 필요(최초 1회).
