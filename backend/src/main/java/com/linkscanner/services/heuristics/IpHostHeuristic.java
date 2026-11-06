package com.linkscanner.services.heuristics;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.linkscanner.entities.Finding;
import com.linkscanner.entities.ParsedUrl;

@Component
class IpHostHeuristic implements Heuristic {

	private static final int WEIGHT = 40;

	@Override
	public Optional<Finding> evaluate(ParsedUrl url) {
		return url.ipAddress()
				? Optional.of(new Finding("IP_HOST", "El link apunta a una dirección IP en vez de a un dominio", WEIGHT))
				: Optional.empty();
	}

}
