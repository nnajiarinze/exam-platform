#!/usr/bin/env python3
"""Validate, publish, deliver, and activate a local internal release via OIDC/PKCE."""

from __future__ import annotations

import argparse
import base64
import hashlib
import html
import json
import re
import secrets
import urllib.parse
import urllib.request
import urllib.error
from http.cookiejar import CookieJar
from pathlib import Path


def credentials(username: str) -> tuple[str, str]:
    realm = json.loads(Path("infrastructure/keycloak/exam-platform-realm.json").read_text())
    user = next(value for value in realm["users"] if value["username"] == username)
    password = next(value["value"] for value in user["credentials"] if value["type"] == "password")
    return user["username"], password


def token(client_id: str="admin-portal", username: str="demo.admin",
          redirect: str="http://127.0.0.1:5173/oidc/callback") -> str:
    verifier = secrets.token_urlsafe(64)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
    params = urllib.parse.urlencode({"client_id": client_id, "redirect_uri": redirect,
        "response_type": "code", "scope": "openid profile email", "code_challenge": challenge,
        "code_challenge_method": "S256", "state": secrets.token_urlsafe(16)})
    jar = CookieJar(); opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    login = opener.open("http://localhost:8090/realms/exam-platform/protocol/openid-connect/auth?" + params).read().decode()
    action = html.unescape(re.search(r'<form[^>]+action="([^"]+)"', login).group(1))
    parsed_action = urllib.parse.urlparse(action)
    action = urllib.parse.urlunparse(parsed_action._replace(scheme="http", netloc="localhost:8090"))
    username, password = credentials(username)
    class StopRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            if newurl.startswith(redirect):
                return None
            return super().redirect_request(req, fp, code, msg, headers, newurl)
    login_opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), StopRedirect())
    try:
        cookie_header = "; ".join(f"{cookie.name}={cookie.value}" for cookie in jar)
        login_opener.open(urllib.request.Request(action, data=urllib.parse.urlencode(
            {"username": username, "password": password, "credentialId": ""}).encode(),
            headers={"Cookie": cookie_header, "Content-Type": "application/x-www-form-urlencoded"}))
        raise RuntimeError("OIDC login did not redirect to the callback")
    except urllib.error.HTTPError as error:
        location = error.headers.get("Location")
        if error.code not in {302, 303} or not location:
            raise
    code = urllib.parse.parse_qs(urllib.parse.urlparse(location).query)["code"][0]
    payload = urllib.parse.urlencode({"grant_type": "authorization_code", "client_id": client_id,
        "redirect_uri": redirect, "code": code, "code_verifier": verifier}).encode()
    response = urllib.request.urlopen(urllib.request.Request(
        "http://localhost:8090/realms/exam-platform/protocol/openid-connect/token", data=payload)).read()
    return json.loads(response)["access_token"]


def api(access_token: str, method: str, path: str, payload: dict | None = None):
    data = None if payload is None else json.dumps(payload).encode()
    request = urllib.request.Request("http://localhost:8082" + path, method=method, data=data,
        headers={"Authorization": "Bearer " + access_token, "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=130) as response:
        return json.loads(response.read())


def main():
    parser=argparse.ArgumentParser();parser.add_argument("release_id");args=parser.parse_args();access=token()
    release=api(access,"GET",f"/api/v1/admin/releases/{args.release_id}")
    validation=api(access,"POST",f"/api/v1/admin/releases/{args.release_id}/validate",{"version":release["version"]})
    if not validation["valid"]: raise RuntimeError("Release validation failed: "+json.dumps(validation))
    release=api(access,"GET",f"/api/v1/admin/releases/{args.release_id}")
    published=api(access,"POST",f"/api/v1/admin/releases/{args.release_id}/publish",{"version":release["version"]})
    delivered=api(access,"POST",f"/api/v1/admin/releases/{args.release_id}/deliver")
    active=api(access,"POST",f"/api/v1/admin/releases/{args.release_id}/activate")
    print(json.dumps({"validation":validation,"published":{"status":published["status"],"checksum":published["checksum"]},
                      "delivered":delivered,"active":{"status":active["status"],"checksum":active["checksum"]}},default=str))


if __name__=="__main__":main()
