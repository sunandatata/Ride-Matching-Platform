package com.rideshare.driver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Driver Service main application entry point.
 * Handles driver profile management, licensing, vehicle details, and performance metrics.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.rideshare.driver", "com.rideshare.shared"})
public class DriverServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriverServiceApplication.class, args);
    }
}
