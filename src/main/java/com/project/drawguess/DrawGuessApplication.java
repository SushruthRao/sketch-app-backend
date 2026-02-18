package com.project.drawguess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DrawGuessApplication {

	public static void main(String[] args) {
		SpringApplication.run(DrawGuessApplication.class, args);
	}

}
