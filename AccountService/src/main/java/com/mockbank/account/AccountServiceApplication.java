package com.mockbank.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

import com.mockbank.commons.security.DefaultSecurityConfig;
import com.mockbank.commons.security.FeignTokenRelayConfig;

@EnableFeignClients(basePackages = "com.mockbank.account.client")
@Import({DefaultSecurityConfig.class, FeignTokenRelayConfig.class})
@SpringBootApplication(scanBasePackages = {"com.mockbank.account", "com.commons"})
public class AccountServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountServiceApplication.class, args);
	}

}
