package com.afternote.global.web;

import com.afternote.global.config.PasskeyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Android Credential Manager 가 RP 도메인에서 앱 패키지·서명을 확인한다.
 * nginx HTTPS location / 가 백엔드로 프록시하므로 별도 nginx 설정은 없다.
 */
@RestController
@RequiredArgsConstructor
public class AssetLinksController {

    private final PasskeyProperties passkeyProperties;

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> assetLinks() {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("namespace", "android_app");
        target.put("package_name", passkeyProperties.getAndroidPackageName());
        target.put("sha256_cert_fingerprints", passkeyProperties.androidSha256Fingerprints());

        Map<String, Object> statement = new LinkedHashMap<>();
        statement.put("relation", List.of("delegate_permission/common.get_login_creds"));
        statement.put("target", target);
        return List.of(statement);
    }
}
