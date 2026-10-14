package com.afternote.domain.notification.repository;
import com.afternote.domain.notification.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PushInstallationRepository extends JpaRepository<PushInstallation, Long> {
    Optional<PushInstallation> findByUser_IdAndInstallationId(Long userId, String installationId);
    boolean existsByUser_IdAndPermission(Long userId, NotificationPermission permission);
    void deleteByUser_Id(Long userId);
}
