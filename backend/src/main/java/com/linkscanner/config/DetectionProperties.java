package com.linkscanner.config;

import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("linkscanner.detection")
public record DetectionProperties(
		List<Brand> brands,
		Set<String> suspiciousTlds,
		Set<String> shorteners,
		Set<String> freeHostingPlatforms,
		List<String> keywords) {

	public record Brand(String name, List<String> keywords, Set<String> officialDomains) {
	}

	public boolean isOfficialDomain(String registrableDomain) {
		return brands.stream().anyMatch(brand -> brand.officialDomains().contains(registrableDomain));
	}

}
