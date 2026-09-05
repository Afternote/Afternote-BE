package com.afternote.global.config;

import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PasskeyPropertiesTest {

    private static final String DEBUG_FINGERPRINT =
            "82:D9:C6:1E:84:22:AE:F0:04:0A:7D:A6:A1:B8:FF:15:AA:55:25:6D:EF:03:0F:DD:08:D4:F5:77:81:2E:C7:FB";
    private static final String DEBUG_APK_KEY_HASH =
            "android:apk-key-hash:gtnGHoQirvAECn2mobj_FapVJW3vAw_dCNT1d4Eux_s";
    private static final String RELEASE_FINGERPRINT =
            "9C:C7:57:27:5C:5B:9C:16:89:09:B3:92:24:F9:8C:A9:00:61:DF:A7:0A:97:80:E9:95:72:C6:D5:FC:1C:08:29";
    private static final String RELEASE_APK_KEY_HASH =
            "android:apk-key-hash:nMdXJ1xbnBaJCbOSJPmMqQBh36cKl4DplXLG1fwcCCk";

    @Test
    @DisplayName("앱 서명 SHA-256 을 android:apk-key-hash origin 으로 변환한다")
    void apkKeyHashOrigin_FromSha256Fingerprint() {
        assertThat(PasskeyProperties.apkKeyHashOrigin(DEBUG_FINGERPRINT)).isEqualTo(DEBUG_APK_KEY_HASH);
        assertThat(PasskeyProperties.apkKeyHashOrigin(RELEASE_FINGERPRINT)).isEqualTo(RELEASE_APK_KEY_HASH);
    }

    @Test
    @DisplayName("신뢰 origin 에 웹 origin 과 지문에서 파생한 Android origin 이 함께 들어간다")
    void trustedOriginUrls_IncludeWebAndAndroid() {
        PasskeyProperties properties = new PasskeyProperties();
        properties.setAndroidSha256(DEBUG_FINGERPRINT + ", " + RELEASE_FINGERPRINT);

        assertThat(properties.trustedOriginUrls()).containsExactlyInAnyOrder(
                "https://afternote.kro.kr",
                DEBUG_APK_KEY_HASH,
                RELEASE_APK_KEY_HASH
        );

        Set<Origin> origins = properties.trustedOriginUrls().stream()
                .map(Origin::new)
                .collect(Collectors.toSet());
        ServerProperty serverProperty = new ServerProperty(origins, properties.getRpId(), new DefaultChallenge());

        assertThat(serverProperty.getOrigins())
                .contains(new Origin(DEBUG_APK_KEY_HASH), new Origin("https://afternote.kro.kr"));
    }

    @Test
    @DisplayName("깨진 지문은 건너뛰고 웹 origin 만 남긴다")
    void trustedOriginUrls_SkipsMalformedFingerprints() {
        PasskeyProperties properties = new PasskeyProperties();
        properties.setAndroidSha256("not-hex, :::, GG:HH");

        assertThat(properties.trustedOriginUrls()).containsExactly("https://afternote.kro.kr");
        assertThat(properties.trustedOriginUrls())
                .allSatisfy(url -> assertThatCode(() -> new Origin(url)).doesNotThrowAnyException());
    }

    @Test
    @DisplayName("실서버 지문 2개는 Origin 생성에서 예외가 나지 않는다")
    void trustedOriginUrls_LiveFingerprintsAreValidOrigins() {
        PasskeyProperties properties = new PasskeyProperties();
        properties.setAndroidSha256(DEBUG_FINGERPRINT + "," + RELEASE_FINGERPRINT);

        assertThat(properties.trustedOriginUrls()).hasSize(3);
        for (String url : properties.trustedOriginUrls()) {
            assertThatCode(() -> new Origin(url)).doesNotThrowAnyException();
        }
    }
}
