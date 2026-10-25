package com.afternote.domain.notification.policy;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
class NonBillingNotificationFlowMySqlTest extends NonBillingNotificationFlowTest {
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");
    @BeforeAll static void start() { MYSQL.start(); }
    @AfterAll static void stop() { MYSQL.stop(); }
    @Test void manualUpgradePreservesExistingNotificationAndBackfillsOnlyFinalRecords() {
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(dataSource());
        jdbc.execute("drop table notification_profile");jdbc.execute("drop table notification_anniversary");
        jdbc.execute("drop table notification_link");jdbc.execute("drop table notification_channel_attempt");jdbc.execute("drop table notification_letter_delivery_run");
        jdbc.execute("alter table notification_dispatch drop column channel,drop column recipient_phone,drop column push_recipient_id,drop column external_recipient,drop column push_accepted");
        jdbc.execute("alter table notification_dispatch modify column status enum('QUEUED','READY','DISPATCHING','PUSH_SENT','PUSH_FAILED','ALIMTALK_PENDING','IN_APP_ONLY','CANCELLED') not null");
        jdbc.execute("alter table diary drop column published_at");jdbc.execute("alter table deep_thought drop column published_at");jdbc.execute("alter table user_daily_question drop column published_at");
        jdbc.update("insert into notification_dispatch(user_id,event_key,category,event,title,body,payload_json,due_at,status) values(999,'legacy','B','SCHEDULED','title','body','{}','2026-10-01 12:00:00','PUSH_SENT')");
        jdbc.update("insert into diary(user_id,title,content,is_draft,entry_date,created_at,updated_at) select id,'final','body',false,'2026-10-01','2026-10-01 12:00:00','2026-10-01 12:00:00' from users limit 1");
        jdbc.update("insert into diary(user_id,title,content,is_draft,entry_date,created_at,updated_at) select id,'draft','body',true,'2026-10-01','2026-10-01 12:00:00','2026-10-01 12:00:00' from users limit 1");
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.FileSystemResource("deploy/sql/20261025_non_billing_notifications.sql")).execute(dataSource());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select push_accepted from notification_dispatch where event_key='legacy'",Boolean.class)).isTrue();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select external_recipient from notification_dispatch where event_key='legacy'",Boolean.class)).isFalse();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from diary where title='final' and published_at=created_at",Integer.class)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from diary where title='draft' and published_at is null",Integer.class)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from notification_profile",Integer.class)).isZero();
        jdbc.update("update notification_dispatch set status='ALIMTALK_SENT' where event_key='legacy'");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select status from notification_dispatch where event_key='legacy'",String.class)).isEqualTo("ALIMTALK_SENT");
    }
    @Test void automaticDataMigrationIsVersionedAndPreservesAlreadyPublishedTimestamps() {
        var source=dataSource();var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.execute("drop table if exists notification_data_migration");
        jdbc.update("insert into diary(user_id,title,content,is_draft,entry_date,created_at,updated_at,published_at) select id,'legacy-final','body',false,'2026-10-01','2026-10-01 12:00:00','2026-10-01 12:00:00',null from users limit 1");
        var migration=new com.afternote.domain.notification.engagement.NotificationDataMigrator(jdbc,source,new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
        migration.run(null);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from diary where title='legacy-final' and published_at=created_at",Integer.class)).isEqualTo(1);
        jdbc.update("update diary set published_at='2026-10-05 12:00:00' where title='legacy-final'");migration.run(null);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from diary where title='legacy-final' and published_at='2026-10-05 12:00:00'",Integer.class)).isEqualTo(1);
    }
    @Override protected DataSource dataSource() { return new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()); }
}
