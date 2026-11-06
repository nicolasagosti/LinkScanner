package com.linkscanner.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyzeControllerTest {

	@Autowired
	private MockMvc mvc;

	private ResultActions analyze(String body) throws Exception {
		return mvc.perform(post("/api/v1/analyze").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void servesTheManualTestPage() throws Exception {
		mvc.perform(get("/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("LinkScanner")));
	}

	@Test
	void returnsVerdictAndReasons() throws Exception {
		analyze("{\"url\":\"https://rnercadopago.com/\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.host").value("rnercadopago.com"))
				.andExpect(jsonPath("$.verdict").value("DANGEROUS"))
				.andExpect(jsonPath("$.score").value(60))
				.andExpect(jsonPath("$.findings[0].code").value("BRAND_LOOKALIKE"));
	}

	@Test
	void rejectsInvalidLinks() throws Exception {
		analyze("{\"url\":\"javascript:alert(1)\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Solo se pueden analizar links http o https"));
	}

	@Test
	void rejectsBlankAndOversizedUrls() throws Exception {
		analyze("{\"url\":\"\"}").andExpect(status().isBadRequest());
		analyze("{}").andExpect(status().isBadRequest());
		analyze("{\"url\":\"https://example.com/" + "a".repeat(2048) + "\"}").andExpect(status().isBadRequest());
	}

}
