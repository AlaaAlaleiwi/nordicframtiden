package com.nordicframtiden;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.nordicframtiden")
public class NordicframtidenApplication {

	public static void main(String[] args) {
		SpringApplication.run(NordicframtidenApplication.class, args);
	}

}
