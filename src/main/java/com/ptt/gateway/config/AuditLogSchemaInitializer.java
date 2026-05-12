package com.ptt.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Runs after Hibernate has finished creating all JPA-managed tables.
 * Drops and recreates the audit_logs partitioned table with its
 * child partitions, sequences, functions, and trigger.
 *
 * Kafka consumers are started AFTER schema initialization is complete
 * to prevent race conditions where consumers query audit_logs before
 * the table exists.
 */
@Slf4j
@Component
public class AuditLogSchemaInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @EventListener(ApplicationReadyEvent.class)
    public void initAuditLogSchema() {
        log.info("[AuditLogSchema] Initializing partitioned audit_logs schema...");
        try {
            dropExisting();
            createParentTable();
            createPartitions();
            createSequences();
            createEventNameFunction();
            createTrigger();
            createIndexes();
            log.info("[AuditLogSchema] audit_logs schema initialized successfully.");
        } catch (Exception e) {
            log.error("[AuditLogSchema] Failed to initialize audit_logs schema: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize audit_logs schema", e);
        }

        try {
            createDashboardFailRateTable();
        } catch (Exception e) {
            log.error("[AuditLogSchema] Failed to create dashboard_fail_rate table: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create dashboard_fail_rate table", e);
        }

        log.info("[AuditLogSchema] Starting Kafka consumers...");
        kafkaListenerEndpointRegistry.start();
        log.info("[AuditLogSchema] Kafka consumers started.");
    }

    // ── Drop ────────────────────────────────────────────────────────────────

    private void dropExisting() {
        log.info("[AuditLogSchema] Dropping existing audit_logs table and sequences...");
        jdbcTemplate.execute("DROP TABLE IF EXISTS audit_logs CASCADE");
        jdbcTemplate.execute("DROP SEQUENCE IF EXISTS seq_event_ae");
        jdbcTemplate.execute("DROP SEQUENCE IF EXISTS seq_event_ah");
        jdbcTemplate.execute("DROP SEQUENCE IF EXISTS seq_event_um");
        jdbcTemplate.execute("DROP SEQUENCE IF EXISTS seq_event_rl");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS generate_event_name(VARCHAR) CASCADE");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS trg_set_event_name() CASCADE");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS create_audit_log_partition(DATE) CASCADE");
    }

    // ── Parent Table ─────────────────────────────────────────────────────────

    private void createParentTable() {
        jdbcTemplate.execute("""
                CREATE TABLE audit_logs (
                    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
                    event_name    VARCHAR(10),
                    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
                    severity      VARCHAR(20)   NOT NULL,
                    status_type   VARCHAR(50),
                    created_by    VARCHAR(255),
                    source        VARCHAR(100)  NOT NULL,
                    client_ip     VARCHAR(100),
                    action        VARCHAR(255),
                    description   TEXT,
                    access_method VARCHAR(50),
                    log_type      VARCHAR(50)   NOT NULL,
                    metadata      JSONB,
                    PRIMARY KEY (id, created_at)
                ) PARTITION BY RANGE (created_at)
                """);
        log.info("[AuditLogSchema] Parent table created.");
    }

    // ── Child Partitions ─────────────────────────────────────────────────────

    private void createPartitions() {
        // 1. Create Stored function to auto-create partitions safely via PL/pgSQL
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION create_audit_log_partition(target_month DATE)
                RETURNS VOID AS $$
                DECLARE
                    partition_name TEXT;
                    start_date     DATE;
                    end_date       DATE;
                BEGIN
                    start_date     := DATE_TRUNC('month', target_month);
                    end_date       := start_date + INTERVAL '1 month';
                    partition_name := 'audit_logs_' || TO_CHAR(start_date, 'YYYY_MM');

                    IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = partition_name) THEN
                        EXECUTE FORMAT(
                            'CREATE TABLE %I PARTITION OF audit_logs FOR VALUES FROM (%L) TO (%L)',
                            partition_name, start_date, end_date
                        );
                    END IF;
                END;
                $$ LANGUAGE plpgsql
                """);

        // 2. Call the stored function safely using parameterized queries
        LocalDate now = LocalDate.now();
        for (int i = 0; i < 3; i++) {
            LocalDate targetDate = now.withDayOfMonth(1).plusMonths(i);
            jdbcTemplate.update("SELECT create_audit_log_partition(?)", targetDate);
            log.info("[AuditLogSchema] Partition creation requested for month: {}", targetDate);
        }
    }

    // ── Sequences ────────────────────────────────────────────────────────────

    private void createSequences() {
        jdbcTemplate.execute("CREATE SEQUENCE seq_event_ae START 1");
        jdbcTemplate.execute("CREATE SEQUENCE seq_event_ah START 1");
        jdbcTemplate.execute("CREATE SEQUENCE seq_event_um START 1");
        jdbcTemplate.execute("CREATE SEQUENCE seq_event_rl START 1");
        log.info("[AuditLogSchema] Sequences created.");
    }

    // ── event_name Generator ─────────────────────────────────────────────────

    private void createEventNameFunction() {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION generate_event_name(p_log_type VARCHAR)
                RETURNS VARCHAR AS $$
                DECLARE
                    seq_val BIGINT;
                    prefix  VARCHAR;
                BEGIN
                    CASE p_log_type
                        WHEN 'ACTION'          THEN prefix := 'AE'; seq_val := NEXTVAL('seq_event_ae');
                        WHEN 'ACCESS_HISTORY'  THEN prefix := 'AH'; seq_val := NEXTVAL('seq_event_ah');
                        WHEN 'USER_MANAGEMENT' THEN prefix := 'UM'; seq_val := NEXTVAL('seq_event_um');
                        WHEN 'NGINX_ACCESS'    THEN prefix := 'RL'; seq_val := NEXTVAL('seq_event_rl');
                        ELSE                        prefix := 'EV'; seq_val := NEXTVAL('seq_event_ae');
                    END CASE;
                    RETURN prefix || LPAD(seq_val::TEXT, 4, '0');
                END;
                $$ LANGUAGE plpgsql
                """);
    }

    // ── Trigger ──────────────────────────────────────────────────────────────

    private void createTrigger() {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION trg_set_event_name()
                RETURNS TRIGGER AS $$
                BEGIN
                    IF NEW.event_name IS NULL THEN
                        NEW.event_name := generate_event_name(NEW.log_type);
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);

        jdbcTemplate.execute("""
                CREATE TRIGGER set_audit_event_name
                BEFORE INSERT ON audit_logs
                FOR EACH ROW EXECUTE FUNCTION trg_set_event_name()
                """);
        log.info("[AuditLogSchema] Trigger created.");
    }

    // ── Indexes ──────────────────────────────────────────────────────────────

    private void createIndexes() {
        jdbcTemplate.execute("CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC)");
        jdbcTemplate.execute("CREATE INDEX idx_audit_logs_source     ON audit_logs (source)");
        jdbcTemplate.execute("CREATE INDEX idx_audit_logs_severity   ON audit_logs (severity)");
        jdbcTemplate.execute("CREATE INDEX idx_audit_logs_log_type   ON audit_logs (log_type)");
        jdbcTemplate.execute("CREATE INDEX idx_audit_logs_created_by ON audit_logs (created_by)");
        log.info("[AuditLogSchema] Indexes created.");
    }

    // ── dashboard_fail_rate ───────────────────────────────────────────────────

    private void createDashboardFailRateTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS dashboard_fail_rate (
                    type        VARCHAR(10)       PRIMARY KEY,
                    week_start  DATE              NOT NULL,
                    messages_in BIGINT            NOT NULL DEFAULT 0,
                    failed      BIGINT            NOT NULL DEFAULT 0,
                    fail_rate   DOUBLE PRECISION  NOT NULL DEFAULT 0.0,
                    updated_at  TIMESTAMP         NOT NULL DEFAULT NOW()
                )
                """);

        // Ensure 2 seed rows exist; no-op if already present
        jdbcTemplate.execute("""
                INSERT INTO dashboard_fail_rate (type, week_start, messages_in, failed, fail_rate, updated_at)
                VALUES ('TAG',  date_trunc('week', now())::date, 0, 0, 0.0, now())
                ON CONFLICT (type) DO NOTHING
                """);
        jdbcTemplate.execute("""
                INSERT INTO dashboard_fail_rate (type, week_start, messages_in, failed, fail_rate, updated_at)
                VALUES ('CALC', date_trunc('week', now())::date, 0, 0, 0.0, now())
                ON CONFLICT (type) DO NOTHING
                """);

        log.info("[AuditLogSchema] dashboard_fail_rate table ready.");
    }
}
