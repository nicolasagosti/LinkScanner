package com.linkscanner.entities;

public enum Verdict {

	/** Es un dominio oficial, o se revisó su contenido y no hubo señales suficientes de riesgo. */
	SAFE,

	/** No hay señales suficientes de riesgo, pero tampoco se pudo verificar. No significa seguro. */
	UNKNOWN,

	SUSPICIOUS,

	DANGEROUS;

	static final int SUSPICIOUS_FROM = 30;
	static final int DANGEROUS_FROM = 60;

	public static Verdict from(int score, boolean officialDomain, ContentScanStatus contentScan) {
		if (score >= DANGEROUS_FROM) {
			return DANGEROUS;
		}
		if (score >= SUSPICIOUS_FROM) {
			return SUSPICIOUS;
		}
		return officialDomain || contentScan == ContentScanStatus.COMPLETED ? SAFE : UNKNOWN;
	}

}
