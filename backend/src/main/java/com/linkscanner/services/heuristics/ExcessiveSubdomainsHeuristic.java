package com.linkscanner.services.heuristics;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.linkscanner.entities.Finding;
import com.linkscanner.entities.ParsedUrl;

@Component
class ExcessiveSubdomainsHeuristic implements Heuristic {

	private static final int WEIGHT = 15;
	private static final int MAX_SUBDOMAINS = 2;

	@Override
	public Optional<Finding> evaluate(ParsedUrl url) {
		long subdomains = url.subdomainLabels().stream().filter(label -> !label.equals("www")).count();
		return subdomains > MAX_SUBDOMAINS
				? Optional.of(new Finding("EXCESSIVE_SUBDOMAINS", "El link tiene demasiados subdominios", WEIGHT))
				: Optional.empty();
	}

}
