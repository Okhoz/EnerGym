package com.energym.app.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 * La dirección de la política de privacidad es una sola: la de la app, la de la ficha de Google Play y la del
 * archivo que publica GitHub Pages (raíz del repositorio → okhoz.github.io/EnerGym/…).
 */
public final class PrivacyPolicyUrlTest {
	private static Path project() {
		Path here = Paths.get("").toAbsolutePath();
		return Files.exists(here.resolve("privacidad.html")) ? here : here.getParent();
	}

	@Test
	public void appListingAndPublishedFileShareOneAddress() throws Exception {
		String url = AccountFragment.PRIVACY_POLICY_URL;
		assertTrue(url, url.startsWith("https://okhoz.github.io/EnerGym/"));
		String file = url.substring("https://okhoz.github.io/EnerGym/".length());
		Path page = project().resolve(file);
		assertTrue("La página publicada existe: " + page, Files.exists(page));
		String html = new String(Files.readAllBytes(page), StandardCharsets.UTF_8);
		assertTrue("Página completa, sin front matter de Jekyll", html.startsWith("<!doctype html>"));
		String listing = new String(Files.readAllBytes(project().resolve("docs/google-play/ficha.md")),
			StandardCharsets.UTF_8);
		assertTrue("La ficha de Google Play usa la misma dirección", listing.contains(url));
	}
}
