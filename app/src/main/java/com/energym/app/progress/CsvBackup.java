package com.energym.app.progress;

import com.energym.app.domain.*;
import com.energym.app.nutrition.BodyMeasures;
import com.energym.app.nutrition.FoodEntry;

import java.time.*;
import java.util.*;

/**
 * MODELO de la copia en CSV (se abre en Excel o Google Sheets): entrenos serie a serie, comidas, peso y medidas.
 * Al importar se recuperan comidas, peso y medidas; los entrenos se guardan ya en tu cuenta y no se duplican.
 */
public final class CsvBackup {
	public static final String HEADER = "tipo,fecha,grupo,nombre,serie,kg,reps,tipo_serie,rpe,cantidad,unidad," +
		"kcal_100,proteina_100,hidratos_100,grasa_100,valor,nota";
	private static final int COLUMNS = 17;

	/** Lo que se puede recuperar de un CSV. */
	public static final class Imported {
		public final List<FoodEntry> foods = new ArrayList<>();
		public final SortedMap<LocalDate, Double> weights = new TreeMap<>();
		public final SortedMap<LocalDate, Map<BodyMeasures.Part, String>> measures = new TreeMap<>();
		public int skipped;
	}

	private CsvBackup() {
	}

	public static String export(List<Session> history, ZoneId zone, List<FoodEntry> foods,
		SortedMap<LocalDate, Double> weights, SortedMap<LocalDate, Map<BodyMeasures.Part, Double>> measures) {
		StringBuilder out = new StringBuilder(HEADER).append('\n');
		List<Session> done = new ArrayList<>();
		for (Session s : history)
			if (s.getStatus() == Session.Status.FINISHED)
				done.add(s);
		done.sort(Comparator.comparingLong(Session::getCompletedAt));
		for (Session s : done) {
			LocalDate day = Instant.ofEpochMilli(s.getCompletedAt()).atZone(zone).toLocalDate();
			for (RoutineItem item : s.getRoutine().getItems()) {
				Exercise e = item.getExercise();
				Map<String, String> values = s.getResults().get(e.getId());
				if (values == null)
					continue;
				String note = values.getOrDefault(SetLog.NOTE, "");
				List<SetLog.Entry> sets = e.getMetricType() == MetricType.STRENGTH ? SetLog.of(values) :
					Collections.emptyList();
				if (sets.isEmpty()) {
					List<String> parts = new ArrayList<>();
					for (Map.Entry<String, String> v : values.entrySet())
						if (!v.getKey().equals(SetLog.NOTE) && !v.getKey().equals(SetLog.KEY))
							parts.add(v.getKey() + "=" + v.getValue());
					row(out, "entreno", day.toString(), s.getRoutine().getTitle(), e.getName(), "", "", "", "", "", "", "",
						"", "", "", "", String.join("; ", parts), note);
					continue;
				}
				for (int i = 0; i < sets.size(); i++) {
					SetLog.Entry x = sets.get(i);
					row(out, "entreno", day.toString(), s.getRoutine().getTitle(), e.getName(), Integer.toString(i + 1),
						SetLog.format(x.kg), Integer.toString(x.reps), x.kind == SetLog.Kind.NORMAL ? "" : x.kind.label,
						x.rpe > 0 ? SetLog.format(x.rpe) : "", "", "", "", "", "", "", "", i == 0 ? note : "");
				}
			}
		}
		List<FoodEntry> sorted = new ArrayList<>(foods);
		sorted.sort(Comparator.comparing((FoodEntry f) -> f.day).thenComparingLong(f -> f.createdAt));
		for (FoodEntry f : sorted)
			row(out, "comida", f.day.toString(), f.meal.name(), f.name, "", "", "", "", "", num(f.amount), f.unit.name(),
				num(f.kcal100), num(f.protein100), num(f.carbs100), num(f.fat100), "", f.barcode);
		for (Map.Entry<LocalDate, Double> w : weights.entrySet())
			row(out, "peso", w.getKey().toString(), "", "", "", "", "", "", "", "", "", "", "", "", "", num(w.getValue()), "");
		for (Map.Entry<LocalDate, Map<BodyMeasures.Part, Double>> d : measures.entrySet())
			for (Map.Entry<BodyMeasures.Part, Double> m : d.getValue().entrySet())
				row(out, "medida", d.getKey().toString(), "", m.getKey().key, "", "", "", "", "", "", m.getKey().unit,
					"", "", "", "", num(m.getValue()), "");
		return out.toString();
	}

	public static Imported parse(String csv) {
		Imported result = new Imported();
		List<List<String>> rows = rows(csv == null ? "" : csv);
		for (int r = 0; r < rows.size(); r++) {
			List<String> c = rows.get(r);
			if (r == 0 && !c.isEmpty() && c.get(0).equals("tipo"))
				continue;
			if (c.size() < COLUMNS) {
				if (!(c.size() == 1 && c.get(0).isEmpty()))
					result.skipped++;
				continue;
			}
			try {
				LocalDate day = LocalDate.parse(c.get(1));
				switch (c.get(0)) {
					case "comida":
						result.foods.add(FoodEntry.create(day, FoodEntry.Meal.valueOf(c.get(2)), plain(c.get(3)), plain(c.get(16)),
							FoodEntry.Unit.valueOf(c.get(10)), d(c.get(9)), d(c.get(11)), d(c.get(12)), d(c.get(13)),
							d(c.get(14))));
						break;
					case "peso":
						double kg = d(c.get(15));
						if (kg < 30 || kg > 300)
							throw new IllegalArgumentException();
						result.weights.put(day, kg);
						break;
					case "medida":
						for (BodyMeasures.Part p : BodyMeasures.Part.values())
							if (p.key.equals(c.get(3)))
								result.measures.computeIfAbsent(day, k -> new EnumMap<>(BodyMeasures.Part.class))
									.put(p, c.get(15));
						break;
					case "entreno":
						break;
					default:
						result.skipped++;
				}
			} catch (RuntimeException e) {
				result.skipped++;
			}
		}
		return result;
	}

	/** Quita la comilla que se puso al exportar para que la hoja no lo tomara como fórmula. */
	private static String plain(String v) {
		return v.length() > 1 && v.charAt(0) == '\'' && "=+-@".indexOf(v.charAt(1)) >= 0 ? v.substring(1) : v;
	}

	private static double d(String v) {
		return Double.parseDouble(v.trim().replace(',', '.'));
	}

	private static String num(double v) {
		return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
	}

	private static void row(StringBuilder out, String... cells) {
		for (int i = 0; i < cells.length; i++) {
			String c = cells[i] == null ? "" : cells[i];
			// Una celda que empieza por =, +, - o @ se abriría como fórmula en una hoja de cálculo.
			if (!c.isEmpty() && "=+-@".indexOf(c.charAt(0)) >= 0 && !c.matches("-?\\d+(\\.\\d+)?"))
				c = "'" + c;
			if (c.contains(",") || c.contains("\"") || c.contains("\n") || c.contains("\r"))
				c = '"' + c.replace("\"", "\"\"") + '"';
			out.append(i == 0 ? "" : ",").append(c);
		}
		out.append('\n');
	}

	static List<List<String>> rows(String csv) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < csv.length(); i++) {
			char ch = csv.charAt(i);
			if (quoted) {
				if (ch == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
					cell.append('"');
					i++;
				} else if (ch == '"')
					quoted = false;
				else
					cell.append(ch);
			} else if (ch == '"')
				quoted = true;
			else if (ch == ',') {
				row.add(cell.toString());
				cell.setLength(0);
			} else if (ch == '\n') {
				row.add(cell.toString());
				cell.setLength(0);
				rows.add(row);
				row = new ArrayList<>();
			} else if (ch != '\r')
				cell.append(ch);
		}
		if (cell.length() > 0 || !row.isEmpty()) {
			row.add(cell.toString());
			rows.add(row);
		}
		return rows;
	}
}
