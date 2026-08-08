# nginx (EC2 배포 정본)

이 디렉터리의 `nginx.conf`가 운영 EC2 `~/deploy/nginx/nginx.conf`의 **정본**입니다.  
`release` 브랜치 배포 시 GitHub Actions가 서버로 동기화한 뒤 `nginx -t` + reload 합니다.

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

`snippets/json-429.conf`는 참고용이며, 내용은 이미 `nginx.conf`에 합쳐져 있습니다.
