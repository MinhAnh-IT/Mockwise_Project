package com.mockwise.interview;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the Spring context against a real Postgres DB that has V1 applied
 * with {@code spring.jpa.hibernate.ddl-auto = validate}. Hibernate throws
 * at startup if any entity column / type / nullability does not match the
 * actual schema, so this test is the cheapest way to catch entity ↔ Flyway
 * drift before it hits the deployed environment.
 *
 * <p>The DB is set up by the surrounding harness (see the README on how to
 * run locally). In CI, switch to Testcontainers — the assertion stays the
 * same.
 */
@SpringBootTest
@ActiveProfiles("schemavalidate")
class JpaSchemaValidationIT {

    @Test
    void contextLoads() {
        // Empty body — startup is the assertion. If Hibernate finds an
        // unmapped column or a type mismatch, ApplicationContext fails to
        // load and this test fails.
    }
}
