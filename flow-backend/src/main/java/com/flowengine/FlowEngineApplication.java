package com.flowengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FlowEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowEngineApplication.class, args);
    }
}
