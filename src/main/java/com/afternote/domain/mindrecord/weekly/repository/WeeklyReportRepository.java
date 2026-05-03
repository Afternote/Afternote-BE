package com.afternote.domain.mindrecord.weekly.repository;

import com.afternote.domain.mindrecord.weekly.model.WeeklyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    List<WeeklyReport> findByUserIdOrderByEndDateDesc(Long userId);

    Optional<WeeklyReport> findByUserIdAndStartDate(Long userId, LocalDateTime startDate);
}
