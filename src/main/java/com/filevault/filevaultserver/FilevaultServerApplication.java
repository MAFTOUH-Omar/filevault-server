package com.filevault.filevaultserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
public class FilevaultServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FilevaultServerApplication.class, args);
    }

}
