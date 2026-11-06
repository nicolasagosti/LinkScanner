package com.linkscanner.services.heuristics;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.linkscanner.config.DetectionProperties;
import com.linkscanner.entities.Finding;
import com.linkscanner.entities.ParsedUrl;

@Component
class ShortenerHeuristic implements Heuristic {

	private static final int WEIGHT = 30;

	private final DetectionProperties properties;

	ShortenerHeuristic(DetectionProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<Finding> evaluate(ParsedUrl url) {
		return properties.shorteners().contains(url.registrableDomain())
				? Optional.of(new Finding("URL_SHORTENER", "Es un link acortado: oculta el destino real", WEIGHT))
				: Optional.empty();
	}

}
