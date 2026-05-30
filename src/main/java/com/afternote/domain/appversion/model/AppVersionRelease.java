package com.afternote.domain.appversion.model;

import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "app_version_release",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_app_version_release_platform_version_code",
                columnNames = {"platform", "version_code"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppVersionRelease extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppPlatform platform;

    @Column(name = "version_code", nullable = false)
    private int versionCode;

    @Column(name = "version_name", length = 32)
    private String versionName;

    @Column(name = "store_url", nullable = false, length = 500)
    private String storeUrl;

    public static AppVersionRelease create(
            AppPlatform platform,
            int versionCode,
            String versionName,
            String storeUrl
    ) {
        AppVersionRelease release = new AppVersionRelease();
        release.platform = platform;
        release.versionCode = versionCode;
        release.versionName = versionName;
        release.storeUrl = storeUrl;
        return release;
    }
}
