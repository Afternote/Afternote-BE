# AfterNote EC2/RDS 스케줄러 (Terraform)

매일 **03:00 KST Stop**, **12:00 KST Start** + **Elastic IP 고정** + Start 시 **SSM으로 docker compose 기동**.

---

## 당신이 채워야 하는 것 (체크리스트)

### 1. 레포 안 — `terraform.tfvars` (필수, git에 올리지 않음)

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
```

| 파일 | 변수 | 설명 |
|------|------|------|
| **`terraform.tfvars`** | `ec2_instance_id` | EC2 콘솔 Instance ID (`i-...`) |
| | `rds_instance_id` | RDS DB identifier |
| | `alert_email` | SNS 알림 받을 이메일 |
| | `deploy_path` | EC2에서 compose 경로 (기본 `/home/ec2-user/deploy`) |
| | `existing_eip_allocation_id` | 기존 EIP 있으면 `eipalloc-...`, 없으면 `""` |

예시는 [`terraform.tfvars.example`](terraform.tfvars.example) 참고.

### 2. 로컬 PC — AWS 인증 (필수)

Terraform 실행하는 PC/CI에 AWS CLI 자격 증명 필요:

```bash
aws configure
# 또는 AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY 환경 변수
```

필요 권한: EC2, RDS, Lambda, IAM, EventBridge Scheduler, SNS, CloudWatch, EIP.

### 3. apply **한 번** 후 — AWS/GitHub/도메인 (필수)

| 어디 | 무엇 | 값 |
|------|------|-----|
| **도메인 DNS** | A 레cord | `terraform output elastic_ip` |
| **GitHub** | Repository → Settings → Secrets → `EC2_HOST` | 동일 Elastic IP |
| **이메일** | SNS 구독 확인 | `alert_email`로 온 **Confirm subscription** 클릭 |

### 4. apply 후 — EC2 SSM (SSM Online 아닐 때만)

Systems Manager → Fleet Manager에서 EC2가 **Online**이 아니면:

```bash
# terraform output ec2_ssm_instance_profile_name 값 사용
aws ec2 associate-iam-instance-profile \
  --instance-id i-YOUR_INSTANCE_ID \
  --iam-instance-profile Name=afternote-ec2-ssm
```

EC2 **재부팅 없이** 몇 분 후 Online으로 바뀌는 경우가 많습니다.

### 5. EC2 서버 — `.env.production` (이미 있어야 함)

Start Lambda는 아래 명령을 SSM으로 실행합니다. **EC2의 `~/deploy/.env.production`** 은 Terraform 밖에서 직접 관리:

```bash
docker compose --env-file .env.production up -d
```

[`/.env.production.example`](../../.env.production.example) 참고.

### 6. GitHub Secrets (배포용, 기존)

| Secret | 설명 |
|--------|------|
| `EC2_HOST` | apply 후 **Elastic IP**로 1회 수정 |
| `EC2_USER` | SSH 사용자 (예: `ec2-user`) |
| `EC2_KEY` | SSH private key |
| `DOCKER_USERNAME` / `DOCKER_PASSWORD` | Docker Hub |

---

## 배포 순서

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
# terraform.tfvars 편집

terraform init
terraform plan
terraform apply

terraform output elastic_ip
# → 도메인 A 레cord + GitHub EC2_HOST 에 설정

# SNS 이메일 Confirm subscription

# 수동 테스트 (스케줄 기다리지 않음)
aws lambda invoke --function-name afternote-stop-env /tmp/afternote-stop.json
# EC2/RDS stopped 확인

aws lambda invoke --function-name afternote-start-env /tmp/afternote-start.json
# RDS available, EC2 running, docker ps, API 확인
```

---

## 스케줄

| 동작 | 시간 (KST) | cron |
|------|------------|------|
| Stop | 03:00 | `cron(0 3 * * ? *)` |
| Start | 12:00 | `cron(0 12 * * ? *)` |

`terraform.tfvars`에서 변경 가능.

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

- **03:00~12:00 KST** 서비스 다운 (단일 환경 전체에 적용)
- **off-hours 중 `release` push** → EC2 stopped면 deploy 실패 → Start Lambda 먼저 invoke
- **RDS Stop 7일** → 자동 Start
- **Elastic IP** stopped 시간에도 Public IPv4 **~$0.005/h** 과금 (~월 $3.6)

---

## 비용 (스케줄러 자체)

| 항목 | 월 예상 |
|------|---------|
| Elastic IP (Public IPv4) | ~$3.6 |
| Lambda + Scheduler + Logs | ~$0 |
| SNS | ~$0 |

EC2/RDS compute off 절감이 훨씬 큼.
