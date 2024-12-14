package com.jercel.tech;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableAsync
public class JercelDeploymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(JercelDeploymentServiceApplication.class, args);
	}

	// @Bean
	// ExecutorService initExecutorService(){
	// 	return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	// } 

}
