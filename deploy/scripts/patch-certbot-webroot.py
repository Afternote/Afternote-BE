#!/usr/bin/env python3
"""Write webroot authenticator + path into a certbot renewal conf."""
from __future__ import annotations

import pathlib
import sys

WEBROOT = "/var/www/certbot"


def patch(text: str, domain: str) -> str:
    lines: list[str] = []
    seen_auth = False
    seen_webroot_path = False
    for line in text.splitlines(True):
        stripped = line.strip()
        if stripped.startswith("authenticator ="):
            lines.append("authenticator = webroot\n")
            seen_auth = True
            continue
        if stripped.startswith("webroot_path ="):
            lines.append(f"webroot_path = {WEBROOT},\n")
            seen_webroot_path = True
            continue
        lines.append(line)
    body = "".join(lines)
    if not seen_auth:
        if "[renewalparams]\n" in body:
            body = body.replace(
                "[renewalparams]\n",
                "[renewalparams]\nauthenticator = webroot\n",
                1,
            )
        else:
            body += "\n[renewalparams]\nauthenticator = webroot\n"
    if not seen_webroot_path:
        body += f"webroot_path = {WEBROOT},\n"
    if "[[webroot_map]]" not in body:
        body += f"[[webroot_map]]\n{domain} = {WEBROOT}\n"
    elif f"{domain} =" not in body:
        body += f"{domain} = {WEBROOT}\n"
    return body


def main() -> int:
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <renewal.conf> <domain>", file=sys.stderr)
        return 2
    path = pathlib.Path(sys.argv[1])
    domain = sys.argv[2]
    path.write_text(patch(path.read_text(), domain))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
