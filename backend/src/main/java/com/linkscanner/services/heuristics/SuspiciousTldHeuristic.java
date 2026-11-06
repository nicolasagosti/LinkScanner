package com.linkscanner.services.heuristics;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.linkscanner.config.DetectionProperties;
import com.linkscanner.entities.Finding;
import com.linkscanner.entities.ParsedUrl;

@Component
class SuspiciousTldHeuristic implements Heuristic {

	private static final int WEIGHT = 15;

	private final DetectionProperties properties;

	SuspiciousTldHeuristic(DetectionProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<Finding> evaluate(ParsedUrl url) {
		return properties.suspiciousTlds().contains(url.publicSuffix())
				? Optional.of(new Finding("SUSPICIOUS_TLD",
						"La terminación ." + url.publicSuffix() + " es muy usada en sitios fraudulentos", WEIGHT))
				: Optional.empty();
	}

}
