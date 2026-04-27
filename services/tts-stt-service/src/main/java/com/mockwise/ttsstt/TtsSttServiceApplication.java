package com.mockwise.ttsstt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableKafka
@EnableScheduling
public class TtsSttServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TtsSttServiceApplication.class, args);
    }
}
