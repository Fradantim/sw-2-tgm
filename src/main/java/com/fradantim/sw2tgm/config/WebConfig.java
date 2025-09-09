package com.fradantim.sw2tgm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WebConfig {
	@Bean
	RestClient restClient(RestClient.Builder builder) {
		return builder.build();
	}
}
