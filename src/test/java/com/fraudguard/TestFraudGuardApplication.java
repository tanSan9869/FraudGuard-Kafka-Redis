package com.fraudguard;

import org.springframework.boot.SpringApplication;

public class TestFraudGuardApplication {

	public static void main(String[] args) {
		SpringApplication.from(FraudGuardApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
