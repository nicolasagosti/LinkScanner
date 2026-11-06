package com.linkscanner.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * @param apiKey si está vacía, VirusTotal queda desactivado
 * @param timeout tiempo máximo de cada llamada
 * @param firstPollDelay cuánto esperar antes de pedir el resultado por primera vez
 * @param pollInterval cada cuánto volver a preguntar. El plan gratuito permite 4 pedidos por minuto
 * @param maxWait a partir de cuánto se da por perdido un análisis que no termina
 * @param minMaliciousEngines cuántos motores tienen que marcarlo para considerarlo malicioso. Un
 * solo motor suele ser un falso positivo
 */
@ConfigurationProperties("linkscanner.virustotal")
public record VirusTotalProperties(
		String apiKey,
		String baseUrl,
		Duration timeout,
		Duration firstPollDelay,
		Duration pollInterval,
		Duration maxWait,
		int minMaliciousEngines) {

	public boolean enabled() {
		return StringUtils.hasText(apiKey);
	}

}
