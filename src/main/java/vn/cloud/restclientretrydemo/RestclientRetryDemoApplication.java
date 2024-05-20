package vn.cloud.restclientretrydemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;
import vn.cloud.restclientretrydemo.hello.HelloServiceClient;

@SpringBootApplication
@EnableRetry
public class RestclientRetryDemoApplication {

	private static final Logger log = LoggerFactory.getLogger(RestclientRetryDemoApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(RestclientRetryDemoApplication.class, args);
	}

	@Bean
	ApplicationRunner applicationRunner(HelloServiceClient helloServiceClient) {
		return args -> {
			String hello = helloServiceClient.getHello();
			log.info("Result: %s".formatted(hello));
		};
	}
}
