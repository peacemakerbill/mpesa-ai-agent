package com.mganji.ganji;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GanjiApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GanjiApiApplication.class, args);
    }
}
