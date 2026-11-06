package com.linkscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param requestsPerMinutePerClient cuántos análisis puede pedir cada cliente por minuto
 * @param scansPerMinute cuántos escaneos externos se inician por minuto, sumando a todos los clientes
 * @param scansPerDay cuántos escaneos externos se inician por día, sumando a todos los clientes
 * @param clientIpHeader header donde el proxy del hosting informa la IP del cliente. Vacío si no hay
 * proxy: sin uno que lo reescriba, cualquiera podría falsificarlo y saltearse el límite
 */
@ConfigurationProperties("linkscanner.limits")
public record LimitsProperties(int requestsPerMinutePerClient, int scansPerMinute, int scansPerDay,
		String clientIpHeader) {
}
