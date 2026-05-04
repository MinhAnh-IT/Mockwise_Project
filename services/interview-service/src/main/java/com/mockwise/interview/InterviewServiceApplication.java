package com.mockwise.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
@EnableScheduling   // turns on the OutboxPoller @Scheduled tick
public class InterviewServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InterviewServiceApplication.class, args);
    }
}
