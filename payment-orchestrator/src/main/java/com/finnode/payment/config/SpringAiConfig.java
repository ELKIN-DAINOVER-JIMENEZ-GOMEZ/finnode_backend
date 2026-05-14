package com.finnode.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración relacionada con Spring AI y propiedades de fraude.
 *
 * Esta clase:
 * 1. Habilita la carga de FraudProperties para que el servicio de detección
 *    de fraude pueda usar el umbral configurado.
 * 2. Configura el bean de ChatClient para consumir las APIs de OpenAI
 *    (o el modelo configurado en application.yml).
 */
@Configuration
@EnableConfigurationProperties(FraudProperties.class)
public class SpringAiConfig {

	/**
	 * Crea el bean de ChatClient que será inyectado en FraudDetectionService.
	 *
	 * ChatClient.Builder es automáticamente proporcionado por Spring AI
	 * basándose en la dependencia spring-ai-starter-model-openai en el pom.xml
	 * y la configuración de spring.ai.openai en application.yml.
	 */
	@Bean
	public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
		return chatClientBuilder.build();
	}

	@Bean
	public ObjectMapper fraudObjectMapper() {
		return JsonMapper.builder().findAndAddModules().build();
	}

	@Bean
	public String fraudSystemPrompt(
			@Value("${fraud.system-prompt:Evalua el riesgo de fraude y responde en JSON}") String systemPrompt
	) {
		return systemPrompt;
	}
}


