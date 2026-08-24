# nginx (EC2 배포 정본)

이 디렉터리의 `nginx.conf`가 운영 EC2 `~/deploy/nginx/nginx.conf`의 **정본**입니다.  
`main` 브랜치 배포 시 GitHub Actions가 서버로 동기화한 뒤 `nginx -t` + reload 합니다.

그린필드처럼 `data/certbot`이 비어 있으면 배포가 임시 self-signed를 넣고 nginx를 띄운 뒤, GitHub Secret `CERTBOT_EMAIL`이 있으면 **webroot**로 Let's Encrypt를 발급합니다. HTTP 80의 `/.well-known/acme-challenge/`가 그 경로입니다. nginx를 멈추고 certbot이 80포트를 잡는 standalone은 쓰지 않습니다.

이후 갱신:

| 경로 | 동작 |
| --- | --- |
| 호스트 systemd `afternote-cert-renew.timer` | 매일 `renew-certs.sh` (잔여 30일 이내면 파일 교체 + nginx reload) |
| `deploy.yml` | 배포마다 webroot 전환 확인. 잔여 30일 이내면 renew, 21일 이하면 배포 실패 |
| `tls-expiry.yml` | 매일 공개 HTTPS 잔여 일수 검사 (21일 이하 실패) |

운영 인증서가 예전에 standalone으로 발급됐어도, 배포·dry-run이 `renewal/*.conf`에 `authenticator=webroot`와 `webroot_path=/var/www/certbot`을 씁니다. 인증서만 webroot로 바꾸고 경로가 없으면 certbot이 웹루트를 물어보다가 실패합니다.

```bash
# 수동 갱신 (nginx 유지)
cd ~/deploy && ./scripts/renew-certs.sh

# ACME만 시험 (파일·reload 없음). 아직 standalone이면 여기서 실패한다.
cd ~/deploy && ./scripts/renew-certs.sh --dry-run

# nginx만 reload
cd ~/deploy && ./scripts/reload-nginx.sh
```

운영 인증서에 `--force-renewal`을 반복하지 마세요. Let's Encrypt rate limit에 걸립니다.

## 포함 내용

- HTTPS / ACME / upstream keepalive
- `limit_req` (20r/s, burst 40)
- **429 → 공통 JSON** (`code` 1429) — #122

```json
{"status":429,"code":1429,"message":"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.","data":null}
```

## 로컬에서 수동 반영

```bash
scp -i ~/.ssh/afternote-server-key.pem deploy/nginx/nginx.conf ec2-user@<EC2_HOST>:~/deploy/nginx/nginx.conf
ssh -i ~/.ssh/afternote-server-key.pem ec2-user@<EC2_HOST> \
  'cd ~/deploy && docker compose exec -T nginx nginx -t && docker compose exec -T nginx nginx -s reload'
```

## GitHub Secrets

배포 자동화에 아래가 실제 EC2와 맞아야 합니다.

| Secret | 예시 |
|--------|------|
| `EC2_HOST` | `3.37.1.210` |
| `EC2_USER` | `ec2-user` |
| `EC2_KEY` | `afternote-server-key.pem` 내용 |
| `CERTBOT_EMAIL` | Let's Encrypt 계정 메일 |

`snippets/json-429.conf`는 참고용이며, 내용은 이미 `nginx.conf`에 합쳐져 있습니다.
