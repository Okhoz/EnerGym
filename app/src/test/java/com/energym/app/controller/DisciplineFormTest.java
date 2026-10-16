package com.energym.app.controller;

import com.energym.app.catalog.CatalogRepository;
import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class DisciplineFormTest {
	@Test
	public void swimmingFormAsksItsOwnQuestionsAndFixesThePool() {
		AppController c = controller();
		c.profileInput("sportId", "swimming");
		Set<String> keys = keys(c);
		assertTrue(keys.contains("detail.stroke"));
		assertTrue(keys.contains("focus"));
		assertFalse("Natación no pregunta el tipo de entrenamiento", keys.contains("category"));
		assertFalse("La piscina es el único entorno", keys.contains("environment"));
		assertFalse("La piscina va incluida", keys.contains("resource.POOL"));
		assertFalse(keys.contains("resource.BARBELL"));
		assertFalse(keys.contains("detail.position"));
	}

	@Test
	public void changingDisciplineSwapsQuestionsAndKeepsValidChoices() {
		AppController c = controller();
		c.profileInput("sportId", "strength");
		c.profileInput("category", "GYM");
		assertTrue(keys(c).contains("resource.BARBELL"));
		assertTrue(keys(c).contains("detail.split"));
		c.profileInput("sportId", "football");
		Set<String> keys = keys(c);
		assertTrue(keys.contains("detail.position"));
		assertFalse(keys.contains("detail.split"));
		assertFalse("Fútbol solo tiene preparación complementaria", keys.contains("focus"));
		assertTrue(keys.contains("environment"));
	}

	@Test
	public void noEquipmentAsksForNoMaterial() {
		AppController c = controller();
		c.profileInput("sportId", "strength");
		c.profileInput("category", "NO_EQUIPMENT");
		for (String key : keys(c))
			assertFalse(key, key.startsWith("resource."));
	}

	@Test
	public void savedProfileKeepsDetailsOnlyForTheChosenDisciplineAndItsMaterial() {
		Store s = new Store();
		AppController c = new AppController(s, catalog());
		c.activate("uid");
		s.loading.success(new AppStore.Snapshot(null, Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList()));
		c.profileInput("sportId", "strength");
		c.profileInput("detail.split", "FULL_BODY");
		c.profileInput("category", "GYM");
		c.profileInput("resource.BARBELL", "true");
		c.profileInput("sportId", "swimming");
		c.profileInput("detail.stroke", "FREESTYLE");
		c.profileInput("detail.sessionMeters", "1500");
		c.saveProfile();
		TrainingProfile p = s.saved;
		assertNotNull(p);
		assertEquals(Environment.POOL, p.getEnvironment());
		assertEquals(Category.SPORT, p.getCategory());
		assertEquals(EnumSet.of(Resource.POOL), p.getResources());
		Map<String, String> expected = new TreeMap<>();
		expected.put("sessionMeters", "1500");
		expected.put("stroke", "FREESTYLE");
		assertEquals(expected, p.getDetails());
	}

	@Test
	public void detailsAreValidatedAndBlankValuesDropped() {
		Map<String, String> d = new HashMap<>();
		d.put("stroke", "  Crol ");
		d.put("empty", " ");
		assertEquals(Collections.singletonMap("stroke", "Crol"),
			new TrainingProfile.Builder().details(d).build().getDetails());
		assertThrows(IllegalArgumentException.class, () -> new TrainingProfile.Builder()
			.details(Collections.singletonMap("bad key", "x")).build());
		char[] longText = new char[TrainingProfile.MAX_DETAIL + 1];
		Arrays.fill(longText, 'a');
		assertThrows(IllegalArgumentException.class, () -> new TrainingProfile.Builder()
			.details(Collections.singletonMap("note", new String(longText))).build());
	}

	@Test
	public void closedAnswersAreListsAndExperienceFillsTheLevel() {
		AppController c = controller();
		c.profileInput("sportId", "strength");
		Map<String, AppController.InputField> f = new HashMap<>();
		for (AppController.InputField field : c.profileFields())
			f.put(field.key, field);
		for (String key : Arrays.asList("daysPerWeek", "durationMinutes", "level", "experience"))
			assertFalse(key + " debe ser una lista", f.get(key).values.isEmpty());
		assertFalse("El permiso del servidor externo ya no se pregunta", f.containsKey("historyConsent"));
		assertTrue(f.get("daysPerWeek").values.contains("7"));
		c.profileInput("experience", "Más de 3 años");
		assertEquals("Avanzado", value(c, "level"));
		c.profileInput("level", "Intermedio");
		assertEquals("Intermedio", value(c, "level"));
		c.profileInput("durationMinutes", "37");
		AppController.InputField custom = null;
		for (AppController.InputField field : c.profileFields())
			if (field.key.equals("durationMinutes")) custom = field;
		assertEquals("Un valor antiguo fuera de la lista no se pierde", "37", custom.values.get(0));
		assertEquals("37 min", custom.labels.get(0));
	}

	private static String value(AppController c, String key) {
		for (AppController.InputField f : c.profileFields())
			if (f.key.equals(key)) return f.value;
		return null;
	}

	private static Set<String> keys(AppController c) {
		Set<String> keys = new LinkedHashSet<>();
		for (AppController.InputField f : c.profileFields())
			keys.add(f.key);
		return keys;
	}

	private static AppController controller() {
		return new AppController(new Store(), catalog());
	}

	private static CatalogRepository catalog() {
		List<Sport> sports = new ArrayList<>();
		for (String id : Arrays.asList("strength", "swimming", "football"))
			sports.add(new Sport(id, id, Sport.CoverageStatus.EXAMPLE_UNREVIEWED,
				Sport.CoverageStatus.EXAMPLE_UNREVIEWED));
		return new CatalogRepository(1, sports, Collections.emptyList());
	}

	static class Store extends AppControllerTest.Store {
		TrainingProfile saved;

		public void profile(TrainingProfile p, Callback<Void> c) {
			saved = p;
			c.success(null);
		}
	}
}
