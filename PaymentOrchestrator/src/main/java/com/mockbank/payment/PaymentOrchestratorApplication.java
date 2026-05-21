package com.mockbank.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.mockbank.commons.security.CurrentUser;
import com.mockbank.commons.security.DefaultSecurityConfig;

@Import({DefaultSecurityConfig.class, CurrentUser.class})
@SpringBootApplication(scanBasePackages = "com.mockbank.payment")
@EnableFeignClients
@EnableKafka
@EnableScheduling
public class PaymentOrchestratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentOrchestratorApplication.class, args);
	}
}
