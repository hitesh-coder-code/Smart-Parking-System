package com.parkingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // needed for the auto-expiry job in ScheduledExpiryService
public class ParkingSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(ParkingSystemApplication.class, args);
    }
}
