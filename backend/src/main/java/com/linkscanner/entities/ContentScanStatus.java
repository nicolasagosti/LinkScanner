package com.linkscanner.entities;

/**
 * Estado del análisis del contenido de la página.
 */
public enum ContentScanStatus {

	/** No se escaneó: no es un sitio público (IP o host interno) o no hay API key. */
	SKIPPED,

	/** Sigue en curso: hay que volver a consultar. */
	PENDING,

	/** Terminó, y su resultado ya está incluido en el veredicto. */
	COMPLETED,

	/** No se pudo verificar el contenido. No significa que esté limpio. */
	UNAVAILABLE

}
