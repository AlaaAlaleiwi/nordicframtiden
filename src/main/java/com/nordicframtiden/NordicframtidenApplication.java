package com.nordicframtiden;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Nordicframtiden Spring Boot application.
 */
@SpringBootApplication
@EnableScheduling
public class NordicframtidenApplication {

    public static void main(String[] args) {
        SpringApplication.run(NordicframtidenApplication.class, args);
    }
}
