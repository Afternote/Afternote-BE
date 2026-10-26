"""Run the production contract command against local fixtures, never production."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class ContractRunnerTest(unittest.TestCase):
    def run_contract(self, status):
        seen = []
        schema = {
            "openapi": "3.0.1",
            "info": {"title": "Contract runner regression", "version": "1"},
            "paths": {"/api/v1/diary": {"get": {
                "parameters": [{"in": "query", "name": "yearMonth", "required": True,
                                "schema": {"type": "string", "pattern": "^[0-9]{4}-[0-9]{2}$"},
                                "example": "2026-03"}],
                "responses": {"200": {"description": "OK"}}
            }}}
        }

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def respond(self, code, body):
                data = json.dumps(body).encode()
                self.send_response(code)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def do_POST(self):
                self.rfile.read(int(self.headers.get("Content-Length", "0")))
                if self.path == "/api/v1/auth/login":
                    self.respond(200, {"data": {"accessToken": "local-fixture-token"}})
                else:
                    self.respond(404, {})

            def do_GET(self):
                if self.path == "/v3/api-docs":
                    self.respond(200, schema)
                elif self.path.startswith("/api/v1/diary"):
                    seen.append(self.headers.get("Authorization"))
                    self.respond(status, {"status": status})
                else:
                    self.respond(404, {})

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        env = dict(os.environ, BASE_URL=f"http://127.0.0.1:{server.server_port}",
                   CONTRACT_TEST_EMAIL="local@example.test", CONTRACT_TEST_PASSWORD="fixture-only",
                   CONTRACT_MAX_EXAMPLES="2", CONTRACT_EXCLUDE_PATH_REGEX="/api/v1/admin")
        try:
            with tempfile.TemporaryDirectory(prefix="contract-fixture-") as working_dir:
                result = subprocess.run(["bash", str(Path(__file__).resolve().with_name("contract-test.sh"))],
                                        cwd=working_dir, env=env, capture_output=True, text=True, timeout=120)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()
        self.assertTrue(seen, result.stdout + result.stderr)
        self.assertTrue(all(value == "Bearer local-fixture-token" for value in seen))
        return result

    def test_get_generation_and_login_succeed(self):
        result = self.run_contract(200)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Contract test OK", result.stdout)

    def test_server_error_still_fails_contract(self):
        result = self.run_contract(500)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn("Contract test OK", result.stdout)
        self.assertIn("Server error", result.stdout)


if __name__ == "__main__":
    unittest.main()
