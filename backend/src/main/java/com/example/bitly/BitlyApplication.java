package com.example.bitly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BitlyApplication {

    public static void main(String[] args) {
        SpringApplication.run(BitlyApplication.class, args);
    }
}
