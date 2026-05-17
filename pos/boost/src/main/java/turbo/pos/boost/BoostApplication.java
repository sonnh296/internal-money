package turbo.pos.boost;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;
import turbo.pos.boost.config.RewardModeProperties;

@SpringBootApplication
@EnableConfigurationProperties(RewardModeProperties.class)
@EnableKafka
public class BoostApplication {

	public static void main(String[] args) {
		SpringApplication.run(BoostApplication.class, args);
	}

}
