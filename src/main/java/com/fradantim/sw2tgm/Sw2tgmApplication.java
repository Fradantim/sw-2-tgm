package com.fradantim.sw2tgm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class Sw2tgmApplication {
	public static void main(String[] args) {
		SpringApplication.run(Sw2tgmApplication.class, args);
	}
}