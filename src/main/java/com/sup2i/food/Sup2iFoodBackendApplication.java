package com.sup2i.food;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class Sup2iFoodBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(Sup2iFoodBackendApplication.class, args);
	}

}
