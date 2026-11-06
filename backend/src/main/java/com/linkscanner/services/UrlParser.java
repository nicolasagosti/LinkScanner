package com.linkscanner.services;

import java.net.IDN;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;

import com.linkscanner.entities.ParsedUrl;
import com.linkscanner.exceptions.InvalidUrlException;

/**
 * Parsea links imitando a los navegadores y no a {@link java.net.URI}: lo que importa es a qué
 * host llegaría realmente el usuario. Los atacantes explotan esa diferencia, por ejemplo con
 * barras invertidas, hosts en {@code %xx} o IPs escritas como un solo número.
 */
@Component
public class UrlParser {

	private static final Pattern WEB_SCHEME = Pattern.compile("^https?:[/\\\\]*", Pattern.CASE_INSENSITIVE);
	// Otro esquema (mailto:, javascript:, ftp://...). Un dígito después de ":" es un puerto.
	private static final Pattern OTHER_SCHEME = Pattern.compile("^[a-z][a-z0-9+.-]*:(?!\\d)", Pattern.CASE_INSENSITIVE);
	private static final Pattern CONTROL_WHITESPACE = Pattern.compile("[\\t\\r\\n]");
	private static final Pattern AUTHORITY_END = Pattern.compile("[/\\\\?#]");
	private static final Pattern PORT = Pattern.compile("\\d{1,5}");
	private static final Pattern VALID_ASCII_HOST = Pattern.compile("[a-z0-9._-]+");
	// Igual que el estándar WHATWG: si la última parte es un número, el navegador lo trata como IPv4.
	private static final Pattern NUMERIC_LABEL = Pattern.compile("\\d+|0x[0-9a-f]*");

	public ParsedUrl parse(String rawUrl) {
		String url = CONTROL_WHITESPACE.matcher(rawUrl).replaceAll("").strip();
		Matcher scheme = WEB_SCHEME.matcher(url);
		String schemeName = "http";
		if (scheme.find()) {
			schemeName = url.substring(0, url.indexOf(':')).toLowerCase(Locale.ROOT);
			url = url.substring(scheme.end());
		}
		else if (OTHER_SCHEME.matcher(url).find()) {
			throw new InvalidUrlException("Solo se pueden analizar links http o https");
		}

		Matcher authorityEnd = AUTHORITY_END.matcher(url);
		int end = authorityEnd.find() ? authorityEnd.start() : url.length();
		String authority = url.substring(0, end);
		String rest = url.substring(end);

		int at = authority.lastIndexOf('@');
		String userInfo = at >= 0 ? authority.substring(0, at) : null;
		String hostAndPort = authority.substring(at + 1);
		String host = stripPort(hostAndPort);
		String prefix = schemeName + "://";
		String suffix = hostAndPort.substring(host.length()) + rest.replace('\\', '/');

		if (host.startsWith("[")) {
			return ipv6(host, userInfo, rest, prefix, suffix);
		}
		return build(toAsciiHost(host), userInfo, rest, prefix, suffix);
	}

	private String stripPort(String hostAndPort) {
		int portSeparator = hostAndPort.lastIndexOf(':');
		// El ":" de una IPv6 está adentro de los corchetes.
		if (portSeparator < 0 || portSeparator < hostAndPort.lastIndexOf(']')) {
			return hostAndPort;
		}
		String port = hostAndPort.substring(portSeparator + 1);
		if (!port.isEmpty() && (!PORT.matcher(port).matches() || Integer.parseInt(port) > 65535)) {
			throw new InvalidUrlException("El puerto del link no es válido");
		}
		return hostAndPort.substring(0, portSeparator);
	}

	private ParsedUrl ipv6(String bracketedHost, String userInfo, String rest, String prefix, String suffix) {
		String address = bracketedHost.endsWith("]") ? bracketedHost.substring(1, bracketedHost.length() - 1) : "";
		if (!InetAddresses.isInetAddress(address) || !address.contains(":")) {
			throw new InvalidUrlException("La dirección IP del link no es válida");
		}
		String host = address.toLowerCase(Locale.ROOT);
		return new ParsedUrl(host, host, host, "", List.of(host), userInfo, normalizeRest(rest), true,
				prefix + "[" + host + "]" + suffix);
	}

	private String toAsciiHost(String rawHost) {
		String decoded = rawHost;
		try {
			if (rawHost.contains("%")) {
				decoded = URLDecoder.decode(rawHost.replace("+", "%2B"), StandardCharsets.UTF_8);
			}
			String ascii = IDN.toASCII(decoded, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
			ascii = ascii.endsWith(".") ? ascii.substring(0, ascii.length() - 1) : ascii;
			if (!VALID_ASCII_HOST.matcher(ascii).matches()) {
				throw new InvalidUrlException("El link no tiene un dominio válido");
			}
			return ascii;
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidUrlException("El link no tiene un dominio válido");
		}
	}

	private ParsedUrl build(String host, String userInfo, String rest, String prefix, String suffix) {
		String path = normalizeRest(rest);
		String normalizedUrl = prefix + host + suffix;
		String lastLabel = host.substring(host.lastIndexOf('.') + 1);
		if (NUMERIC_LABEL.matcher(lastLabel).matches()) {
			return new ParsedUrl(host, host, host, "", List.of(host), userInfo, path, true, normalizedUrl);
		}

		InternetDomainName domain;
		try {
			domain = InternetDomainName.from(host);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidUrlException("El link no tiene un dominio válido");
		}

		String registrable = domain.isUnderPublicSuffix() ? domain.topPrivateDomain().toString() : host;
		String publicSuffix = domain.hasPublicSuffix() && domain.isUnderPublicSuffix() ? domain.publicSuffix().toString() : "";
		String withoutSuffix = publicSuffix.isEmpty() ? host : host.substring(0, host.length() - publicSuffix.length() - 1);
		List<String> labels = Arrays.stream(withoutSuffix.split("\\.")).map(IDN::toUnicode).toList();
		return new ParsedUrl(host, IDN.toUnicode(host), registrable, publicSuffix, labels, userInfo, path, false,
				normalizedUrl);
	}

	private String normalizeRest(String rest) {
		String lower = rest.toLowerCase(Locale.ROOT);
		try {
			return URLDecoder.decode(lower.replace("+", "%2B"), StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException ex) {
			// Un %xx roto no invalida el link: el navegador lo abre igual.
			return lower;
		}
	}

}
