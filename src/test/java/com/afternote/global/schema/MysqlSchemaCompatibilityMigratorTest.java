package com.afternote.global.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MysqlSchemaCompatibilityMigratorTest {

    @Mock
    JdbcTemplate jdbcTemplate;
    @Mock
    DataSource dataSource;
    @Mock
    Connection connection;
    @Mock
    DatabaseMetaData metaData;

    @Test
    @DisplayName("MySQL 이 아니면 스키마 보정을 건너뛴다")
    void skipNonMysql() throws Exception {
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.getMetaData()).willReturn(metaData);
        given(metaData.getDatabaseProductName()).willReturn("H2");

        new MysqlSchemaCompatibilityMigrator(jdbcTemplate, dataSource)
                .run(new DefaultApplicationArguments());

        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class));
        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    @DisplayName("ENUM 컬럼을 VARCHAR 로 바꾸고 CHECK 를 제거한다")
    void convertEnumAndDropChecks() throws Exception {
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.getMetaData()).willReturn(metaData);
        given(metaData.getDatabaseProductName()).willReturn("MySQL");

        given(jdbcTemplate.query(contains("DATA_TYPE = 'enum'"), any(RowMapper.class)))
                .willAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                    given(rs.getString("TABLE_NAME")).willReturn("afternote");
                    given(rs.getString("COLUMN_NAME")).willReturn("category_type");
                    given(rs.getObject("CHARACTER_MAXIMUM_LENGTH")).willReturn(8);
                    given(rs.getInt("CHARACTER_MAXIMUM_LENGTH")).willReturn(8);
                    given(rs.getString("IS_NULLABLE")).willReturn("YES");
                    return List.of(mapper.mapRow(rs, 0));
                });
        given(jdbcTemplate.query(contains("CONSTRAINT_TYPE = 'CHECK'"), any(RowMapper.class)))
                .willAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                    given(rs.getString("TABLE_NAME")).willReturn("afternote");
                    given(rs.getString("CONSTRAINT_NAME")).willReturn("afternote_chk_1");
                    return List.of(mapper.mapRow(rs, 0));
                });
        given(jdbcTemplate.query(contains("emotion_category"), any(org.springframework.jdbc.core.ResultSetExtractor.class)))
                .willReturn("YES");
        given(jdbcTemplate.query(contains("time_letter_receiver"), any(org.springframework.jdbc.core.ResultSetExtractor.class)))
                .willReturn("YES");
        given(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), eq("time_letters"), eq("delivered_at")))
                .willReturn(0);
        given(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("users"), anyString()))
                .willReturn(1);

        new MysqlSchemaCompatibilityMigrator(jdbcTemplate, dataSource)
                .run(new DefaultApplicationArguments());

        verify(jdbcTemplate).execute(
                "ALTER TABLE `afternote` MODIFY COLUMN `category_type` VARCHAR(32) NULL");
        verify(jdbcTemplate).execute("ALTER TABLE `afternote` DROP CHECK `afternote_chk_1`");
        verify(jdbcTemplate, never()).update(argThat((String sql) ->
                sql.contains("tl.delivery_mode = 'POST_DEATH'")
                        && sql.contains("SET tlr.delivered_at = NULL")
        ));
    }

    @Test
    @DisplayName("수신자 전달 시각을 정규화하고 중복된 타임레터 전달 시각 컬럼을 제거한다")
    void makeTimeLetterReceiverDeliveredAtNullable() throws Exception {
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.getMetaData()).willReturn(metaData);
        given(metaData.getDatabaseProductName()).willReturn("MySQL");

        given(jdbcTemplate.query(contains("DATA_TYPE = 'enum'"), any(RowMapper.class)))
                .willReturn(List.of());
        given(jdbcTemplate.query(contains("CONSTRAINT_TYPE = 'CHECK'"), any(RowMapper.class)))
                .willReturn(List.of());
        given(jdbcTemplate.query(contains("emotion_category"), any(org.springframework.jdbc.core.ResultSetExtractor.class)))
                .willReturn("YES");
        given(jdbcTemplate.query(contains("time_letter_receiver"), any(org.springframework.jdbc.core.ResultSetExtractor.class)))
                .willReturn("NO");
        given(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), eq("time_letters"), eq("delivered_at")))
                .willReturn(1);
        given(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("users"), anyString()))
                .willReturn(1);

        new MysqlSchemaCompatibilityMigrator(jdbcTemplate, dataSource)
                .run(new DefaultApplicationArguments());

        verify(jdbcTemplate).execute(
                "ALTER TABLE time_letter_receiver MODIFY COLUMN delivered_at DATETIME(6) NULL"
        );
        verify(jdbcTemplate).update(argThat((String sql) ->
                sql.contains("tl.delivery_mode = 'POST_DEATH'")
                        && sql.contains("SET tlr.delivered_at = NULL")
        ));
        verify(jdbcTemplate).update(contains("tl.status <> 'SENT'"));
        verify(jdbcTemplate, times(2)).update(contains("tl.status = 'SENT'"));
        verify(jdbcTemplate, times(2)).update(contains("dc.state = 'FULFILLED'"));
        verify(jdbcTemplate).update(contains("SELECT DISTINCT tlr.time_letter_id"));
        verify(jdbcTemplate).execute("ALTER TABLE time_letters DROP COLUMN delivered_at");
    }

    @Test
    @DisplayName("category_type 이 NULL 허용이면 NOT NULL 로 보정한다")
    void makeAfternoteCategoryTypeNotNull() throws Exception {
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.getMetaData()).willReturn(metaData);
        given(metaData.getDatabaseProductName()).willReturn("MySQL");

        given(jdbcTemplate.query(contains("DATA_TYPE = 'enum'"), any(RowMapper.class)))
                .willReturn(List.of());
        given(jdbcTemplate.query(contains("CONSTRAINT_TYPE = 'CHECK'"), any(RowMapper.class)))
                .willReturn(List.of());
        given(jdbcTemplate.query(contains("emotion_category"), any(org.springframework.jdbc.core.ResultSetExtractor.class)))
                .willReturn("YES");
        given(jdbcTemplate.query(contains("time_letter_receiver"), any(org.springframework.jdbc.core.ResultSetExtractor.class)))
                .willReturn("YES");
        given(jdbcTemplate.query(contains("COLUMN_NAME = 'category_type'"), any(org.springframework.jdbc.core.ResultSetExtractor.class)))
                .willReturn("YES");
        given(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM afternote WHERE category_type IS NULL"),
                eq(Integer.class)))
                .willReturn(0);
        given(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), eq("time_letters"), eq("delivered_at")))
                .willReturn(0);
        given(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("users"), anyString()))
                .willReturn(1);

        new MysqlSchemaCompatibilityMigrator(jdbcTemplate, dataSource)
                .run(new DefaultApplicationArguments());

        verify(jdbcTemplate).execute(
                "ALTER TABLE afternote MODIFY COLUMN category_type VARCHAR(20) NOT NULL"
        );
    }

    @Test
    @DisplayName("category_type NULL 행이 있으면 NOT NULL 보정을 거부한다")
    void rejectAfternoteCategoryTypeNotNullWhenNullRowsExist() throws Exception {
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.getMetaData()).willReturn(metaData);
        given(metaData.getDatabaseProductName()).willReturn("MySQL");

        given(jdbcTemplate.query(contains("DATA_TYPE = 'enum'"), any(RowMapper.class)))
                .willReturn(List.of());
        given(jdbcTemplate.query(contains("CONSTRAINT_TYPE = 'CHECK'"), any(RowMapper.class)))
                .willReturn(List.of());
        given(jdbcTemplate.query(contains("emotion_category"), any(org.springframework.jdbc.core.ResultSetExtractor.class)))
                .willReturn("YES");
        given(jdbcTemplate.query(contains("time_letter_receiver"), any(org.springframework.jdbc.core.ResultSetExtractor.class)))
                .willReturn("YES");
        given(jdbcTemplate.query(contains("COLUMN_NAME = 'category_type'"), any(org.springframework.jdbc.core.ResultSetExtractor.class)))
                .willReturn("YES");
        given(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM afternote WHERE category_type IS NULL"),
                eq(Integer.class)))
                .willReturn(2);
        given(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), eq("time_letters"), eq("delivered_at")))
                .willReturn(0);
        given(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("users"), anyString()))
                .willReturn(1);

        assertThatThrownBy(() -> new MysqlSchemaCompatibilityMigrator(jdbcTemplate, dataSource)
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MySQL schema compatibility migration failed")
                .hasRootCauseMessage("afternote.category_type has 2 NULL row(s); cannot apply NOT NULL (#240)");
    }

    @Test
    @DisplayName("식별자에 허용되지 않은 문자가 있으면 거부한다")
    void rejectUnsafeIdentifier() {
        assertThatThrownBy(() -> MysqlSchemaCompatibilityMigrator.quote("afternote;drop"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
