package com.energym.app.visual;

import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Catálogo real de ejercicios para pruebas JVM (sin org.json de Android). */
public final class TestCatalog {
	private TestCatalog() {
	}

	public static List<Exercise> exercises() throws Exception {
		Map<?, ?> root = (Map<?, ?>) MiniJson.parse(new String(
			Files.readAllBytes(Paths.get("src/main/assets/catalog.json")), StandardCharsets.UTF_8));
		List<Exercise> exercises = new ArrayList<>();
		for (Object value : (List<?>) root.get("exercises")) {
			Map<?, ?> row = (Map<?, ?>) value;
			exercises.add(new Exercise((String) row.get("id"), (String) row.get("name"),
				MetricType.valueOf((String) row.get("metricType")), enums(row.get("goals"), Goal.class),
				enums(row.get("environments"), Environment.class), enums(row.get("requiredResources"), Resource.class),
				new LinkedHashSet<>(strings(row.get("sportIds"))), Exercise.Coverage.valueOf((String) row.get("coverage")),
				Boolean.TRUE.equals(row.get("reviewed")), (String) row.get("description"), strings(row.get("alternativeIds"))));
		}
		return exercises;
	}

	private static List<String> strings(Object values) {
		List<String> result = new ArrayList<>();
		for (Object value : (List<?>) values) result.add((String) value);
		return result;
	}

	private static <E extends Enum<E>> Set<E> enums(Object values, Class<E> type) {
		Set<E> result = EnumSet.noneOf(type);
		for (String value : strings(values)) result.add(Enum.valueOf(type, value));
		return result;
	}
}
