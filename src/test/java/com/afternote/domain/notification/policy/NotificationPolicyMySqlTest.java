package com.afternote.domain.notification.policy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import javax.sql.DataSource;

/** Exercises the same persisted policy under MySQL's default REPEATABLE_READ. */
class NotificationPolicyMySqlTest extends NotificationPolicyTest {
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");
    @BeforeAll static void startDatabase() { MYSQL.start(); }
    @AfterAll static void stopDatabase() { MYSQL.stop(); }
    @Override protected DataSource createDataSource() {
        return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
