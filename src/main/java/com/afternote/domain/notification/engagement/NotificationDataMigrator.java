package com.afternote.domain.notification.engagement;

import java.sql.Connection;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** One-time data upgrade also covers environments using Hibernate ddl-auto=update. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class NotificationDataMigrator implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final PlatformTransactionManager transactions;
    @Override public void run(ApplicationArguments args) {
        try (Connection connection=dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) return;
        } catch (java.sql.SQLException ex) { throw new IllegalStateException("Cannot detect notification migration database",ex); }
        jdbc.execute("create table if not exists notification_data_migration (version varchar(64) primary key, applied_at datetime(6) null)");
        String version="20261025_non_billing_v1";
        jdbc.update("insert ignore into notification_data_migration(version,applied_at) values(?,null)",version);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var applied=jdbc.queryForList("select applied_at from notification_data_migration where version=? for update",version).get(0).get("applied_at");
            if (applied != null) return;
            jdbc.update("update diary set published_at=created_at where published_at is null and is_draft=false");
            jdbc.update("update deep_thought set published_at=created_at where published_at is null and is_draft=false");
            jdbc.update("update user_daily_question set published_at=created_at where published_at is null and is_draft=false and is_answered=true");
            jdbc.update("update notification_dispatch set push_accepted=true where status='PUSH_SENT'");
            jdbc.update("update time_letter_receiver tr join time_letters tl on tl.id=tr.time_letter_id set tr.delivered_at=null where tl.status<>'SENT' and tl.delivery_mode='DATE'");
            jdbc.update("update notification_data_migration set applied_at=current_timestamp(6) where version=?",version);
        });
    }
}
