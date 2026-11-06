package com.linkscanner.services.heuristics;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.linkscanner.config.DetectionProperties;
import com.linkscanner.entities.Finding;
import com.linkscanner.entities.ParsedUrl;

/**
 * Busca palabras típicas de phishing. Pesa poco a propósito: "/login" es normal en un sitio
 * legítimo, así que solo importa combinada con otras señales.
 */
@Component
class KeywordHeuristic implements Heuristic {

	private static final int WEIGHT = 20;

	private final DetectionProperties properties;

	KeywordHeuristic(DetectionProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<Finding> evaluate(ParsedUrl url) {
		if (properties.isOfficialDomain(url.registrableDomain())) {
			return Optional.empty();
		}
		String text = withoutAccents(url.unicodeHost() + url.pathAndQuery());
		List<String> found = properties.keywords().stream().filter(text::contains).toList();
		return found.isEmpty() ? Optional.empty()
				: Optional.of(new Finding("PHISHING_KEYWORDS",
						"Contiene palabras típicas de phishing: " + String.join(", ", found), WEIGHT));
	}

	private String withoutAccents(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

}
