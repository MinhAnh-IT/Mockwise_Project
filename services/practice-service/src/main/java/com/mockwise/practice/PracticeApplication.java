package com.mockwise.practice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Practice-service: LeetCode-style algorithm practice.
 *
 * <p>Browses question-bank's coding catalog (Feign), dispatches Run/Submit to
 * judge-service over Kafka, and owns the per-user submission history, problem
 * status and stats in its own database. Scheduling drives the watchdog that
 * fails submissions stuck awaiting a verdict.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
@EnableScheduling
public class PracticeApplication {
    public static void main(String[] args) {
        SpringApplication.run(PracticeApplication.class, args);
    }
}
