package com.payments.orch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@SpringBootApplication(scanBasePackages = {"com.payments", "com.commons"})
@EnableFeignClients
@EnableKafka
@EnableScheduling
@EnableAsync
public class PaymentOrchestratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentOrchestratorApplication.class, args);
	}

	/** Thread pool riêng cho reward sync bất đồng bộ — tách khỏi Kafka consumer thread */
	@Bean(name = "rewardSyncExecutor")
	public Executor rewardSyncExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(5);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("reward-sync-");
		executor.initialize();
		return executor;
	}
}
