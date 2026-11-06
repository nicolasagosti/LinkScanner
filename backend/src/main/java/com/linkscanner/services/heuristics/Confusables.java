package com.linkscanner.services.heuristics;

import java.util.Locale;

import com.ibm.icu.text.SpoofChecker;

/**
 * Reduce un texto a su "esqueleto" visual (Unicode TR39): dos textos que se ven parecidos dan el
 * mismo resultado. Por ejemplo, la "а" cirílica equivale a la "a" latina, "rn" a "m" y "0" a "o".
 */
final class Confusables {

	private static final SpoofChecker SKELETONS = new SpoofChecker.Builder().build();

	private Confusables() {
	}

	static String skeleton(String text) {
		return SKELETONS.getSkeleton(text).toLowerCase(Locale.ROOT);
	}

	static boolean isAscii(String text) {
		return text.chars().allMatch(c -> c < 128);
	}

}
