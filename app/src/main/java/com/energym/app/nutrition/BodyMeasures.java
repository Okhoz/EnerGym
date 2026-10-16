package com.energym.app.nutrition;

import java.time.LocalDate;
import java.util.*;

/**
 * MODELO de medidas corporales por día (cintura, pecho, brazo…, en cm, y % de grasa). Se guardan solo en este móvil,
 * por cuenta, en un texto simple: «2026-10-16;waist=82.5;chest=100».
 */
public final class BodyMeasures {
	public interface Store {
		String load(String owner);

		void save(String owner, String encoded);
	}

	public enum Part {
		WAIST("waist", "Cintura", "cm"), CHEST("chest", "Pecho", "cm"), ARM("arm", "Brazo", "cm"),
		HIPS("hips", "Cadera", "cm"), THIGH("thigh", "Muslo", "cm"), FAT("fat", "Grasa corporal", "%");

		public final String key, label, unit;

		Part(String key, String label, String unit) {
			this.key = key;
			this.label = label;
			this.unit = unit;
		}
	}

	private final Store store;
	private String owner;
	private SortedMap<LocalDate, Map<Part, Double>> days = new TreeMap<>();

	public BodyMeasures(Store store) {
		this.store = store;
	}

	public void activate(String next) {
		owner = next;
		days = owner == null ? new TreeMap<>() : decode(store.load(owner));
	}

	public SortedMap<LocalDate, Map<Part, Double>> days() {
		return Collections.unmodifiableSortedMap(days);
	}

	/** Guarda las medidas escritas de un día (texto con coma o punto); devuelve un error o "". */
	public String save(LocalDate day, Map<Part, String> typed) {
		if (owner == null)
			return "Entra en tu cuenta para guardar medidas.";
		Map<Part, Double> values = new EnumMap<>(Part.class);
		for (Map.Entry<Part, String> e : typed.entrySet()) {
			String t = e.getValue() == null ? "" : e.getValue().trim().replace(',', '.');
			if (t.isEmpty())
				continue;
			double v;
			try {
				v = Double.parseDouble(t);
			} catch (NumberFormatException ex) {
				return e.getKey().label + ": escribe un número.";
			}
			double max = e.getKey() == Part.FAT ? 70 : 250;
			if (!(v > 0) || v > max)
				return e.getKey().label + ": entre 0 y " + (int) max + " " + e.getKey().unit + ".";
			values.put(e.getKey(), Math.round(v * 10) / 10.0);
		}
		if (values.isEmpty())
			return "Escribe al menos una medida.";
		days.put(day, values);
		store.save(owner, encode(days));
		return "";
	}

	public void remove(LocalDate day) {
		if (owner == null)
			return;
		days.remove(day);
		store.save(owner, encode(days));
	}

	/** Primera y última medida de una parte (para «−3 cm desde…»); null si hay menos de dos. */
	public double[] change(Part part) {
		Double first = null, last = null;
		for (Map<Part, Double> m : days.values())
			if (m.containsKey(part)) {
				if (first == null)
					first = m.get(part);
				last = m.get(part);
			}
		return countOf(part) < 2 ? null : new double[]{first, last};
	}

	private int countOf(Part part) {
		int n = 0;
		for (Map<Part, Double> m : days.values())
			if (m.containsKey(part))
				n++;
		return n;
	}

	static String encode(SortedMap<LocalDate, Map<Part, Double>> days) {
		StringBuilder s = new StringBuilder();
		for (Map.Entry<LocalDate, Map<Part, Double>> d : days.entrySet()) {
			s.append(d.getKey());
			for (Map.Entry<Part, Double> v : d.getValue().entrySet())
				s.append(';').append(v.getKey().key).append('=').append(v.getValue());
			s.append('\n');
		}
		return s.toString();
	}

	static SortedMap<LocalDate, Map<Part, Double>> decode(String text) {
		SortedMap<LocalDate, Map<Part, Double>> result = new TreeMap<>();
		if (text == null)
			return result;
		for (String line : text.split("\n")) {
			String[] parts = line.trim().split(";");
			try {
				LocalDate day = LocalDate.parse(parts[0]);
				Map<Part, Double> values = new EnumMap<>(Part.class);
				for (int i = 1; i < parts.length; i++) {
					String[] kv = parts[i].split("=");
					for (Part p : Part.values())
						if (p.key.equals(kv[0]))
							values.put(p, Double.parseDouble(kv[1]));
				}
				if (!values.isEmpty())
					result.put(day, values);
			} catch (RuntimeException ignored) {
				// Línea dañada: se ignora.
			}
		}
		return result;
	}
}
