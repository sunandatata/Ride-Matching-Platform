package com.rideshare.ride;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot application entry point for the Ride Service.
 * Core microservice for ride lifecycle management.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.rideshare.ride", "com.rideshare.shared"})
public class RideServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideServiceApplication.class, args);
    }
}
