package com.linkscanner.services;

import java.time.Duration;

import com.linkscanner.entities.Finding;

/**
 * Un servicio externo que analiza links. Para sumar otro alcanza con crear un {@code @Component}
 * que implemente esta interfaz. Si el servicio falla, lanza
 * {@link org.springframework.web.client.RestClientException}.
 */
public interface ContentScanner {

	String name();

	boolean enabled();

	Finding maliciousFinding();

	/** Cuánto esperar antes de la primera consulta. */
	Duration firstPollDelay();

	Duration pollInterval();

	/** A partir de cuánto se da por perdido un análisis que no termina. */
	Duration maxWait();

	/** Pide el análisis y devuelve su identificador en ese servicio. */
	String submit(String url);

	ScanOutcome check(String scanId);

	enum ScanOutcome {

		PENDING, CLEAN, MALICIOUS,

		/** El análisis terminó sin un veredicto, que no es lo mismo que dar limpio. */
		NO_VERDICT

	}

}
