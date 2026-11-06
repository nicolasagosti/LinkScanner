package com.linkscanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LinkscannerBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(LinkscannerBackendApplication.class, args);
	}

}
