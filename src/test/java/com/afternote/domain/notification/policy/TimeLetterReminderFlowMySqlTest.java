package com.afternote.domain.notification.policy;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

class TimeLetterReminderFlowMySqlTest extends TimeLetterReminderFlowTest {
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");
    @BeforeAll static void startDatabase() { MYSQL.start(); }
    @AfterAll static void stopDatabase() { MYSQL.stop(); }
    @Override protected DataSource createDataSource() {
        return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
