package com.afternote.domain.appversion.repository;

import com.afternote.domain.appversion.model.AppPlatform;
import com.afternote.domain.appversion.model.AppVersionRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppVersionReleaseRepository extends JpaRepository<AppVersionRelease, Long> {

    Optional<AppVersionRelease> findFirstByPlatformOrderByVersionCodeDesc(AppPlatform platform);

    List<AppVersionRelease> findAllByPlatformOrderByVersionCodeDesc(AppPlatform platform);

    boolean existsByPlatformAndVersionCode(AppPlatform platform, int versionCode);
}
