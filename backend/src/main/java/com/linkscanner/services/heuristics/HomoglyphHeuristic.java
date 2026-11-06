package com.linkscanner.services.heuristics;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ibm.icu.text.SpoofChecker;
import com.linkscanner.entities.Finding;
import com.linkscanner.entities.ParsedUrl;

/**
 * Detecta caracteres engañosos. Punycode por sí solo no alcanza para marcar un link: un dominio
 * legítimo con "ñ" también usa {@code xn--}.
 */
@Component
class HomoglyphHeuristic implements Heuristic {

	private static final int WEIGHT = 40;

	private final SpoofChecker scriptChecker = new SpoofChecker.Builder()
			.setChecks(SpoofChecker.RESTRICTION_LEVEL | SpoofChecker.INVISIBLE | SpoofChecker.MIXED_NUMBERS)
			.setRestrictionLevel(SpoofChecker.RestrictionLevel.HIGHLY_RESTRICTIVE)
			.build();

	@Override
	public Optional<Finding> evaluate(ParsedUrl url) {
		boolean deceptive = url.labels().stream()
				.filter(label -> !Confusables.isAscii(label))
				.anyMatch(label -> scriptChecker.failsChecks(label)
						|| Confusables.isAscii(Confusables.skeleton(label)));
		return deceptive
				? Optional.of(new Finding("HOMOGLYPH", "El dominio usa caracteres que imitan a otras letras", WEIGHT))
				: Optional.empty();
	}

}
