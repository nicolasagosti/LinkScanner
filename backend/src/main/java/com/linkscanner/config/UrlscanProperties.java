package com.linkscanner.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * @param apiKey si está vacía, el análisis de contenido queda desactivado
 * @param visibility {@code private} o {@code unlisted}. Nunca {@code public}: dejaría los links
 * de los usuarios visibles para cualquiera
 * @param timeout tiempo máximo de cada llamada a urlscan
 * @param firstPollDelay cuánto esperar antes de pedir el resultado por primera vez
 * @param pollInterval cada cuánto volver a preguntar
 * @param maxWait a partir de cuánto se da por perdido un escaneo que no termina
 */
@ConfigurationProperties("linkscanner.urlscan")
public record UrlscanProperties(
		String apiKey,
		String baseUrl,
		String visibility,
		Duration timeout,
		Duration firstPollDelay,
		Duration pollInterval,
		Duration maxWait) {

	public boolean enabled() {
		return StringUtils.hasText(apiKey);
	}

}
