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
	public void closedAnswersAreListsTimeIsInHoursAndExperienceFillsTheLevel() {
		AppController c = controller();
		c.profileInput("sportId", "strength");
		Map<String, AppController.InputField> f = fields(c);
		assertFalse(f.get("daysPerWeek").values.isEmpty());
		assertFalse(f.get("level").values.isEmpty());
		assertTrue("La experiencia necesita detalle: texto libre", f.get("experience").values.isEmpty());
		assertTrue("El tiempo admite cualquier valor: se escribe", f.get("durationHours").values.isEmpty());
		assertFalse("El permiso del servidor externo ya no se pregunta", f.containsKey("historyConsent"));
		c.profileInput("durationHours", "0:20");
		assertEquals("0:20", value(c, "durationHours"));
		assertEquals("20", c.profileValue("durationMinutes"));
		c.profileInput("durationHours", "1:30 h");
		assertEquals("90", c.profileValue("durationMinutes"));
		assertEquals("1:30", value(c, "durationHours"));
		assertEquals("Solo horas también vale", "120", AppController.minutes("2"));
		assertEquals("45", AppController.minutes("0.75"));
		assertEquals("2:00", AppController.hours("120"));
		assertEquals("0:05", AppController.hours("5"));
		assertEquals("horas no válidas", AppController.minutes("0:75"));
		assertEquals("horas no válidas", AppController.minutes("hora"));
		c.profileInput("experience", "8 años de fuerza");
		assertEquals("Avanzado", value(c, "level"));
		c.profileInput("experience", "6 meses");
		assertEquals("Principiante", value(c, "level"));
		c.profileInput("level", "Intermedio");
		c.profileInput("experience", "Hago yoga");
		assertEquals("Sin tiempo indicado no cambia el nivel", "Intermedio", value(c, "level"));
	}

	private static Map<String, AppController.InputField> fields(AppController c) {
		Map<String, AppController.InputField> f = new HashMap<>();
		for (AppController.InputField field : c.profileFields())
			f.put(field.key, field);
		return f;
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
