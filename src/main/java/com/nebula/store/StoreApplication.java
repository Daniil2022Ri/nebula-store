package com.nebula.store;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the Nebula Store application.
 *
 * <p>The application serves a single-page storefront from {@code src/main/resources/static}
 * and exposes a JSON API under {@code /api}.</p>
 */
@SpringBootApplication
@EnableScheduling
public class StoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreApplication.class, args);
    }
}
