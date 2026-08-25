package com.kholodilin.statemachine.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sample Spring Boot application that registers an order-saga definition.
 */
@SpringBootApplication
public class DemoApplication {

    /**
     * @param args standard Spring Boot arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
