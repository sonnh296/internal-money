package com.mockbank.biller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.mockbank.commons.security.CurrentUser;
import com.mockbank.commons.security.DefaultSecurityConfig;

@Import({DefaultSecurityConfig.class, CurrentUser.class})
@SpringBootApplication(scanBasePackages = "com.mockbank.biller")
@EnableFeignClients(basePackages = "com.mockbank.biller")
@EnableKafka
@EnableScheduling
public class BillPaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BillPaymentServiceApplication.class, args);
	}

}
