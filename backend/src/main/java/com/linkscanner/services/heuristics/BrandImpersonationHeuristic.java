package com.linkscanner.services.heuristics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.linkscanner.config.DetectionProperties;
import com.linkscanner.config.DetectionProperties.Brand;
import com.linkscanner.entities.Finding;
import com.linkscanner.entities.ParsedUrl;

/**
 * Detecta dominios que no son de una marca pero la imitan o la mencionan.
 */
@Component
class BrandImpersonationHeuristic implements Heuristic {

	private static final int LOOKALIKE_WEIGHT = 60;
	private static final int MENTION_WEIGHT = 40;

	// Con palabras cortas, una letra de diferencia da demasiados falsos positivos.
	private static final int MIN_LENGTH_FOR_SUBSTRING = 6;
	private static final int MIN_LENGTH_FOR_TYPO = 8;
	private static final int MIN_LENGTH_FOR_TWO_TYPOS = 11;

	private final DetectionProperties properties;

	BrandImpersonationHeuristic(DetectionProperties properties) {
		this.properties = properties;
	}

	@Override
	public Optional<Finding> evaluate(ParsedUrl url) {
		if (url.ipAddress() || properties.isOfficialDomain(url.registrableDomain())) {
			return Optional.empty();
		}
		Optional<Finding> mention = Optional.empty();
		for (Brand brand : properties.brands()) {
			if (embedsOfficialDomain(url, brand)) {
				return Optional.of(new Finding("BRAND_DOMAIN_EMBEDDED",
						"Usa el dominio de " + brand.name() + " como parte de otro, pero el sitio real es "
								+ url.registrableDomain(),
						LOOKALIKE_WEIGHT));
			}
			for (String keyword : brand.keywords()) {
				Match match = match(url, keyword);
				if (match == Match.LOOKALIKE) {
					return Optional.of(new Finding("BRAND_LOOKALIKE",
							"El dominio se parece a " + brand.name() + ", pero no es el oficial", LOOKALIKE_WEIGHT));
				}
				if (match == Match.MENTION && mention.isEmpty()) {
					mention = Optional.of(new Finding("BRAND_MENTION",
							"Menciona a " + brand.name() + ", pero no es un dominio oficial", MENTION_WEIGHT));
				}
			}
		}
		return mention;
	}

	/**
	 * Solo cuenta si el dominio oficial está en los subdominios: mercadopago.com.ar.evil.xyz lo
	 * imita, mientras que mercadolibre.com.mx es un dominio distinto de mercadolibre.com.
	 */
	private boolean embedsOfficialDomain(ParsedUrl url, Brand brand) {
		String subdomains = url.host().substring(0, url.host().length() - url.registrableDomain().length());
		return brand.officialDomains().stream().anyMatch(domain -> ("." + subdomains).contains("." + domain + "."));
	}

	private Match match(ParsedUrl url, String keyword) {
		String keywordSkeleton = Confusables.skeleton(keyword);
		Match best = Match.NONE;
		for (String label : url.labels()) {
			for (String token : tokens(label)) {
				if (token.equals(keyword)) {
					best = Match.MENTION;
				}
				else if (looksLike(token, keywordSkeleton, keyword)) {
					return Match.LOOKALIKE;
				}
			}
			if (best == Match.NONE && keyword.length() >= MIN_LENGTH_FOR_SUBSTRING) {
				// Sin separadores: "mercadopagologin", o "rnercadopagologin" si además la imita.
				String joined = label.replace("-", "");
				if (joined.contains(keyword)) {
					best = Match.MENTION;
				}
				else if (Confusables.skeleton(joined).contains(keywordSkeleton)) {
					return Match.LOOKALIKE;
				}
			}
		}
		return best;
	}

	private List<String> tokens(String label) {
		List<String> tokens = new ArrayList<>(Arrays.asList(label.split("-")));
		tokens.add(label);
		return tokens;
	}

	private boolean looksLike(String token, String keywordSkeleton, String keyword) {
		String tokenSkeleton = Confusables.skeleton(token);
		if (tokenSkeleton.equals(keywordSkeleton)) {
			return true;
		}
		int allowedTypos = keyword.length() >= MIN_LENGTH_FOR_TWO_TYPOS ? 2
				: keyword.length() >= MIN_LENGTH_FOR_TYPO ? 1 : 0;
		return allowedTypos > 0 && levenshtein(tokenSkeleton, keywordSkeleton) <= allowedTypos;
	}

	private int levenshtein(String a, String b) {
		int[] previous = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			previous[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			int[] current = new int[b.length() + 1];
			current[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
				current[j] = Math.min(substitution, Math.min(previous[j], current[j - 1]) + 1);
			}
			previous = current;
		}
		return previous[b.length()];
	}

	private enum Match {
		NONE, MENTION, LOOKALIKE
	}

}
