# nginx rate limit 429 JSON (#122)

운영 EC2의 `~/deploy/nginx/nginx.conf`가 rate limit(HTML 429)을 반환하면 앱이 파싱하지 못합니다.  
아래를 적용하면 공통 API JSON과 같은 형식으로 맞춥니다.

```json
{"status":429,"code":1429,"message":"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.","data":null}
```

## 적용 방법

1. 서버에서 현재 conf 백업
   ```bash
   cp ~/deploy/nginx/nginx.conf ~/deploy/nginx/nginx.conf.bak.$(date +%Y%m%d)
   ```
2. `snippets/json-429.conf` 내용을 `server { ... }` (또는 rate limit이 걸린 server)에 반영
   - `limit_req`를 쓰는 location이 있으면 `limit_req_status 429;` 유지
   - `error_page 429 = @afternote_too_many_requests;` 와 named location 추가
3. 문법 검사 후 reload
   ```bash
   docker compose exec nginx nginx -t
   docker compose exec nginx nginx -s reload
   # 또는 호스트 nginx라면: sudo nginx -t && sudo nginx -s reload
   ```

## 검증

- `Content-Type: application/json`
- body에 `status`/`code`/`message`/`data` 키 존재
- 운영에 과도한 부하를 주지 않도록 스테이징·로컬에서만 rate limit을 유발해 확인

앱 ErrorCode: `RATE_LIMIT_EXCEEDED(1429)`. OpenAPI Components → Responses → `TooManyRequests`.
