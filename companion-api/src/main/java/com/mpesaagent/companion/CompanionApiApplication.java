package com.mpesaagent.companion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CompanionApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompanionApiApplication.class, args);
    }
}
