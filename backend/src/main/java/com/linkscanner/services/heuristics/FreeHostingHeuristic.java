package com.linkscanner.services.heuristics;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.linkscanner.config.DetectionProperties;
import com.linkscanner.entities.Finding;
import com.linkscanner.entities.ParsedUrl;

/**
 * Cerca de la mitad del feed de OpenPhish está en estas plataformas, pero también hay muchos
 * sitios legítimos. Pesa poco a propósito: solo importa combinada con otras señales.
 */
@Component
class FreeHostingHeuristic implements Heuristic {

	private static final int WEIGHT = 15;

	private final DetectionProperties properties;

	FreeHostingHeuristic(DetectionProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<Finding> evaluate(ParsedUrl url) {
		return properties.freeHostingPlatforms().stream()
				.filter(platform -> url.host().endsWith("." + platform))
				.findFirst()
				.map(platform -> new Finding("FREE_HOSTING",
						"Está en " + platform + ", una plataforma donde cualquiera puede publicar un sitio gratis",
						WEIGHT));
	}

}
