package com.linkscanner.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.linkscanner.entities.ParsedUrl;
import com.linkscanner.exceptions.InvalidUrlException;

class UrlParserTest {

	private final UrlParser parser = new UrlParser();

	@ParameterizedTest
	@CsvSource({
			"https://www.mercadopago.com.ar/login, www.mercadopago.com.ar, mercadopago.com.ar",
			"mercadopago.com.ar, mercadopago.com.ar, mercadopago.com.ar",
			"HTTP://EXAMPLE.COM:8080/x, example.com, example.com",
			"example.com:8080/x, example.com, example.com",
			"https://login.mercadopago.com.ar.xyz/, login.mercadopago.com.ar.xyz, ar.xyz",
			"https://example.com./, example.com, example.com",
			"https://evil.com\\@mercadopago.com.ar/, evil.com, evil.com",
			"https://mercadopago.com.ar@evil.com/, evil.com, evil.com",
			"https://mercadopago.com.ar%2eevil.com/, mercadopago.com.ar.evil.com, evil.com",
			"https:/\\evil.com/, evil.com, evil.com",
	})
	void resolvesTheHostABrowserWouldVisit(String raw, String host, String registrable) {
		ParsedUrl url = parser.parse(raw);
		assertThat(url.host()).isEqualTo(host);
		assertThat(url.registrableDomain()).isEqualTo(registrable);
		assertThat(url.ipAddress()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://192.168.0.1/", "http://2130706433/", "http://0x7f.0.0.1/", "http://[::1]:8080/" })
	void recognizesIpAddresses(String raw) {
		assertThat(parser.parse(raw).ipAddress()).isTrue();
	}

	@Test
	void ignoresTabsAndNewlinesLikeBrowsersDo() {
		assertThat(parser.parse(" https://exa\tmple.com/\n").host()).isEqualTo("example.com");
	}

	@ParameterizedTest
	@CsvSource({
			"example.com/a, http://example.com/a",
			"HTTPS://Usuario:Clave@EXAMPLE.com:8080\\a?b=C, https://example.com:8080/a?b=C",
			"http://[::1]:8080/x, http://[::1]:8080/x",
	})
	void buildsAVisitableUrlWithoutCredentials(String raw, String expected) {
		assertThat(parser.parse(raw).normalizedUrl()).isEqualTo(expected);
	}

	@Test
	void normalizedUrlUsesPunycode() {
		assertThat(parser.parse("https://ñandú.com.ar/").normalizedUrl())
				.isEqualTo("https://" + java.net.IDN.toASCII("ñandú.com.ar") + "/");
	}

	@Test
	void convertsInternationalDomains() {
		ParsedUrl url = parser.parse("https://ñandú.com.ar/");
		assertThat(url.host()).startsWith("xn--");
		assertThat(url.unicodeHost()).isEqualTo("ñandú.com.ar");
		assertThat(url.domainLabel()).isEqualTo("ñandú");
	}

	@ParameterizedTest
	@ValueSource(strings = { "javascript:alert(1)", "mailto:a@example.com", "ftp://example.com", "https://",
			"https://exa mple.com", "http://example.com:99999/", "http://[nope]/", "https://a..b.com" })
	void rejectsWhatIsNotAnAnalyzableWebLink(String raw) {
		assertThatThrownBy(() -> parser.parse(raw)).isInstanceOf(InvalidUrlException.class);
	}

}
