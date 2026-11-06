package com.linkscanner.entities;

import java.util.List;

/**
 * Un link ya normalizado.
 *
 * @param host host en minúsculas y en ASCII (punycode si corresponde)
 * @param unicodeHost el mismo host, tal como se ve en pantalla
 * @param registrableDomain dominio real según la Public Suffix List. Si el host es una IP o
 * su sufijo no existe en la lista, es el host completo
 * @param publicSuffix sufijo público (por ejemplo {@code com.ar}), vacío si no hay
 * @param labels partes del host en Unicode, sin el sufijo. La última es el nombre del dominio
 * @param userInfo lo que venía antes de la {@code @}, o {@code null}
 * @param pathAndQuery todo lo que sigue al host, en minúsculas y decodificado
 * @param ipAddress si el host es una dirección IP
 * @param normalizedUrl link listo para visitar, con esquema y sin lo que venía antes de la {@code @}
 */
public record ParsedUrl(
		String host,
		String unicodeHost,
		String registrableDomain,
		String publicSuffix,
		List<String> labels,
		String userInfo,
		String pathAndQuery,
		boolean ipAddress,
		String normalizedUrl) {

	public String domainLabel() {
		return labels.getLast();
	}

	public List<String> subdomainLabels() {
		return labels.subList(0, labels.size() - 1);
	}

}
