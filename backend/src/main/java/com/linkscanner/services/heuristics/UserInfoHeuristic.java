package com.linkscanner.services.heuristics;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.linkscanner.entities.Finding;
import com.linkscanner.entities.ParsedUrl;

/**
 * En {@code https://mercadopago.com.ar@sitio-falso.com} el navegador va a sitio-falso.com.
 */
@Component
class UserInfoHeuristic implements Heuristic {

	private static final int WEIGHT = 60;

	@Override
	public Optional<Finding> evaluate(ParsedUrl url) {
		return url.userInfo() == null ? Optional.empty()
				: Optional.of(new Finding("USERINFO_TRICK",
						"El link esconde su destino real con una @: en realidad lleva a " + url.unicodeHost(), WEIGHT));
	}

}
