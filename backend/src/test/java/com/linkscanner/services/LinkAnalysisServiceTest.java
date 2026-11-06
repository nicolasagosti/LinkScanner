package com.linkscanner.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.linkscanner.dtos.AnalyzeResponse;
import com.linkscanner.entities.Finding;
import com.linkscanner.entities.Verdict;

@SpringBootTest
class LinkAnalysisServiceTest {

	@Autowired
	private LinkAnalysisService service;

	@ParameterizedTest
	@CsvSource({
			"https://www.mercadopago.com.ar/login/verificar",
			"https://www.bbva.com.ar/",
			"https://onlinebanking.bancogalicia.com.ar/login",
			"https://www.afip.gob.ar/",
			"https://www.mercadolibre.com.mx/",
			"https://www.mercadolivre.com.br/",
			"https://www.mercadopago.com.br/",
			"https://www.santander.com.br/",
			"https://www.anses.gov.ar/",
			"https://www.paypal.com/signin",
	})
	void officialDomainsAreSafe(String url) {
		AnalyzeResponse result = service.analyze(url);
		assertThat(result.verdict()).isEqualTo(Verdict.SAFE);
		assertThat(result.officialDomain()).isTrue();
	}

	@ParameterizedTest
	@CsvSource({
			"https://ñandú.com.ar/",
			"https://github.com/login",
			"https://www.google.com/search?q=seguridad",
			"https://mercadona.es/",
			"https://macroeconomia.com/",
	})
	void linksWithoutSignalsOrVerificationAreUnknownNotSafe(String url) {
		assertThat(service.analyze(url).verdict()).isEqualTo(Verdict.UNKNOWN);
	}

	@Test
	void anotherCountryVersionOfABrandIsNotReportedAsEmbedded() {
		AnalyzeResponse result = service.analyze("https://www.mercadopago.com.uy/");
		assertThat(result.verdict()).isEqualTo(Verdict.SUSPICIOUS);
		assertThat(result.findings()).extracting(Finding::code).containsExactly("BRAND_MENTION");
	}

	@Test
	void anOfficialDomainInsideSubdomainsIsStillReportedWhenTheSuffixIsReused() {
		assertThat(service.analyze("https://login.mercadopago.com.ar.xyz/").findings())
				.extracting(Finding::code).contains("BRAND_DOMAIN_EMBEDDED");
	}

	@Test
	void freeHostingIsAWeakSignalThatDoesNotApplyToThePlatformItself() {
		AnalyzeResponse hosted = service.analyze("https://u8nxq-0r4-shnd.pages.dev/");
		assertThat(hosted.findings()).extracting(Finding::code).containsExactly("FREE_HOSTING");
		assertThat(hosted.verdict()).isEqualTo(Verdict.UNKNOWN);
		assertThat(service.analyze("https://pages.dev/").findings()).isEmpty();
	}

	@Test
	void freeHostingAddsUpWithABrandMention() {
		AnalyzeResponse result = service.analyze("https://prestamo-santander-cliente.vercel.app/");
		assertThat(result.findings()).extracting(Finding::code).containsExactly("BRAND_MENTION", "FREE_HOSTING");
		assertThat(result.score()).isEqualTo(55);
	}

	@ParameterizedTest
	@CsvSource({
			"https://paypa1.ar/, BRAND_LOOKALIKE",
			"https://раураl.com/, BRAND_LOOKALIKE",
			"https://rnercadopago.com/, BRAND_LOOKALIKE",
			"https://mercadopag0.com/, BRAND_LOOKALIKE",
			"https://mercaodpago.com/, BRAND_LOOKALIKE",
			"https://mеrcadopago.com/, BRAND_LOOKALIKE",
			"https://rnercadopago-login.com/, BRAND_LOOKALIKE",
			"https://mercadopago.com.ar.evil.xyz/, BRAND_DOMAIN_EMBEDDED",
			"https://mercadopago.com.ar@evil.com/, USERINFO_TRICK",
			"https://bbva-seguridad.xyz/, BRAND_MENTION",
			"https://galicia-verificacion.com/, BRAND_MENTION",
	})
	void phishingLinksAreDangerous(String url, String expectedFinding) {
		AnalyzeResponse result = service.analyze(url);
		assertThat(result.verdict()).isEqualTo(Verdict.DANGEROUS);
		assertThat(result.findings()).extracting(Finding::code).contains(expectedFinding);
	}

	@ParameterizedTest
	@CsvSource({
			"https://bit.ly/abc123, URL_SHORTENER",
			"http://192.168.0.1/, IP_HOST",
			"http://2130706433/, IP_HOST",
			"https://bancogalicia-promos.com/, BRAND_MENTION",
			"https://соса.com/, HOMOGLYPH",
	})
	void riskySignalsAreSuspicious(String url, String expectedFinding) {
		AnalyzeResponse result = service.analyze(url);
		assertThat(result.verdict()).isEqualTo(Verdict.SUSPICIOUS);
		assertThat(result.findings()).extracting(Finding::code).contains(expectedFinding);
	}

	@Test
	void weakSignalsAloneDoNotRaiseTheVerdict() {
		AnalyzeResponse result = service.analyze("https://mi-tienda.xyz/");
		assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
		assertThat(result.findings()).extracting(Finding::code).containsExactly("SUSPICIOUS_TLD");
	}

	@Test
	void countsSubdomainsWithoutWww() {
		assertThat(service.analyze("https://www.a.b.example.com/").findings()).isEmpty();
		assertThat(service.analyze("https://a.b.c.example.com/").findings())
				.extracting(Finding::code).containsExactly("EXCESSIVE_SUBDOMAINS");
	}

	@Test
	void findsAccentedKeywordsAndAddsWeights() {
		AnalyzeResponse result = service.analyze("https://bbva-ayuda.xyz/verificaci%C3%B3n");
		assertThat(result.findings()).extracting(Finding::code)
				.containsExactly("BRAND_MENTION", "PHISHING_KEYWORDS", "SUSPICIOUS_TLD");
		assertThat(result.score()).isEqualTo(75);
	}

	@Test
	void capsTheScoreAt100() {
		AnalyzeResponse result = service.analyze("https://user@rnercadopago.a.b.c.xyz/login");
		assertThat(result.score()).isEqualTo(100);
	}

}
