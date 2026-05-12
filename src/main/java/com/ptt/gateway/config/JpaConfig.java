package com.ptt.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Separate @Configuration for JPA Auditing.
 *
 * Keeping @EnableJpaAuditing in its own @Configuration class (instead of on
 * @SpringBootApplication) ensures that @WebMvcTest slices do NOT try to load
 * this configuration, avoiding the "JPA metamodel must not be empty" error
 * during controller tests.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaConfig {
}
