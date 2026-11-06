package com.linkscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param requestsPerMinutePerClient cuántos análisis puede pedir cada IP por minuto
 * @param scansPerMinute cuántos escaneos externos se inician por minuto, sumando a todos los clientes
 * @param scansPerDay cuántos escaneos externos se inician por día, sumando a todos los clientes
 */
@ConfigurationProperties("linkscanner.limits")
public record LimitsProperties(int requestsPerMinutePerClient, int scansPerMinute, int scansPerDay) {
}
