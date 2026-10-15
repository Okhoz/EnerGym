package com.energym.app.nutrition;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * CONTROLADOR de nutrición: diario por día, objetivos estimados, escáner y estadísticas. Sin Android.
 */
public final class NutritionController {
	public static final int HISTORY_DAYS = 28;

	public final Map<String, String> bodyDraft = new LinkedHashMap<>();
	public final Map<String, String> foodDraft = new LinkedHashMap<>();
	public BodyProfile body;
	public List<FoodEntry> entries = Collections.emptyList();
	public SortedMap<LocalDate, Double> weights = new TreeMap<>();
	public LocalDate selected;
	public FoodProduct product;
	public boolean busy, looking;
	public String status = "";
	private final NutritionStore store;
	private final NutritionStore.Lookup lookup;
	private final Supplier<LocalDate> clock;
	private final Runnable changed;
	private final java.util.function.Consumer<String> error;
	private String owner;
	private long generation;
	private long foodRevision, loadRequest;
	private FoodEntry editing;
	private SortedMap<LocalDate, Double> dailyCalories = new TreeMap<>();

	public NutritionController(NutritionStore store, NutritionStore.Lookup lookup, Supplier<LocalDate> clock,
		Runnable changed, java.util.function.Consumer<String> error) {
		this.store = store;
		this.lookup = lookup;
		this.clock = clock;
		this.changed = changed;
		this.error = error;
		selected = clock.get();
	}

	public LocalDate today() {
		return clock.get();
	}

	public void activate(String next) {
		if (Objects.equals(owner, next))
			return;
		owner = next;
		generation++;
		foodRevision++;
		body = null;
		entries = Collections.emptyList();
		weights = new TreeMap<>();
		dailyCalories = new TreeMap<>();
		product = null;
		editing = null;
		busy = false;
		looking = false;
		status = "";
		selected = clock.get();
		bodyDraft.clear();
		foodDraft.clear();
		reload();
	}

	public long scopeRevision() { return generation; }

	public String owner() {
		return owner;
	}

	public void reload() {
		if (owner == null) {
			changed.run();
			return;
		}
		long token = generation, request = ++loadRequest;
		LocalDate recentFrom = clock.get().minusDays(HISTORY_DAYS - 1);
		LocalDate selectedFrom = selected.minusDays(6);
		store.load(owner, recentFrom.isBefore(selectedFrom) ? recentFrom : selectedFrom,
			new NutritionStore.Callback<NutritionStore.Snapshot>() {
				public void success(NutritionStore.Snapshot s) {
					if (token != generation || request != loadRequest)
						return;
					body = s.body;
					entries = s.entries;
					weights = new TreeMap<>(s.weights);
					dailyCalories = new TreeMap<>(s.dailyCalories);
					changed.run();
				}

				public void failure(Exception e) {
					if (token == generation && request == loadRequest)
						error.accept("No se pudo leer tu diario: " + e.getMessage());
				}
			});
	}

	public void select(LocalDate day) {
		if (day.isAfter(clock.get()))
			return;
		selected = day;
		reload();
	}

	public List<FoodEntry> entriesOn(LocalDate day) {
		List<FoodEntry> result = new ArrayList<>();
		for (FoodEntry e : entries)
			if (e.day.equals(day))
				result.add(e);
		result.sort(Comparator.comparing((FoodEntry e) -> e.meal).thenComparingLong(e -> e.createdAt));
		return result;
	}

	/**
	 * Totales del día: kcal, proteínas, hidratos y grasas.
	 */
	public double[] totals(LocalDate day) {
		double[] t = new double[4];
		for (FoodEntry e : entries)
			if (e.day.equals(day)) {
				t[0] += e.kcal();
				t[1] += e.protein();
				t[2] += e.carbs();
				t[3] += e.fat();
			}
		return t;
	}

	public BodyProfile.Targets targets() {
		return body == null ? null : body.targets();
	}

	/**
	 * Calorías de los últimos {@code days} días terminando en el seleccionado (antiguo → reciente).
	 */
	public double[] caloriesSeries(int days) {
		double[] values = new double[days];
		for (int i = 0; i < days; i++)
			values[i] = totals(selected.minusDays(days - 1 - i))[0];
		return values;
	}

	public String[] dayLabels(int days) {
		String[] labels = new String[days];
		String[] names = {"L", "M", "X", "J", "V", "S", "D"};
		for (int i = 0; i < days; i++)
			labels[i] = names[selected.minusDays(days - 1 - i).getDayOfWeek().getValue() - 1];
		return labels;
	}

	/**
	 * Balance medio de los días con registros en la última semana. Null si no hay objetivo o registros.
	 */
	public Double weeklyBalance() {
		if (body == null)
			return null;
		double total = 0;
		int days = 0;
		for (int i = 0; i < 7; i++) {
			double kcal = totals(clock.get().minusDays(i))[0];
			if (kcal > 0) {
				total += kcal - body.maintenance();
				days++;
			}
		}
		return days == 0 ? null : total / days;
	}

	public Set<LocalDate> loggedDays() {
		return new TreeSet<>(dailyCalories.keySet());
	}

	public double historyCalories(LocalDate day) {
		return dailyCalories.getOrDefault(day, 0.0);
	}

	// ---- Perfil físico ----

	public void fillBody() {
		bodyDraft.clear();
		BodyProfile b = body;
		bodyDraft.put("sex", b == null ? "FEMALE" : b.sex.name());
		bodyDraft.put("age", b == null ? "" : Integer.toString(b.age));
		bodyDraft.put("heightCm", b == null ? "" : format(b.heightCm));
		bodyDraft.put("weightKg", b == null ? "" : format(b.weightKg));
		bodyDraft.put("activity", b == null ? "MODERATE" : b.activity.name());
		bodyDraft.put("goal", b == null ? "MAINTAIN" : b.goal.name());
		bodyDraft.put("allergies", b == null ? "" : b.allergies);
	}

	public void saveBody(Runnable done) {
		if (owner == null || busy)
			return;
		BodyProfile next;
		try {
			next = new BodyProfile(BodyProfile.Sex.valueOf(bodyDraft.get("sex")),
				Integer.parseInt(bodyDraft.getOrDefault("age", "").trim()), number(bodyDraft.get("heightCm")),
				number(bodyDraft.get("weightKg")), BodyProfile.Activity.valueOf(bodyDraft.get("activity")),
				BodyProfile.Goal.valueOf(bodyDraft.get("goal")), bodyDraft.get("allergies"));
		} catch (RuntimeException e) {
			error.accept("Revisa tus datos: " + message(e, "edad, altura y peso son obligatorios."));
			return;
		}
		busy = true;
		long token = generation;
		LocalDate day = clock.get();
		store.saveBody(owner, next, new NutritionStore.Callback<Void>() {
			public void success(Void v) {
				if (token != generation)
					return;
				body = next;
				store.weight(owner, day, next.weightKg, new NutritionStore.Callback<Void>() {
					public void success(Void ignored) {
						if (token != generation)
							return;
						busy = false;
						weights.put(day, next.weightKg);
						status = "Datos guardados. Objetivos diarios recalculados.";
						done.run();
						changed.run();
					}

					public void failure(Exception e) {
						fail(token, e);
					}
				});
			}

			public void failure(Exception e) {
				fail(token, e);
			}
		});
	}

	public void logWeight(String text) {
		if (owner == null || busy)
			return;
		double kg;
		try {
			kg = number(text);
			if (!(kg >= 30 && kg <= 300))
				throw new IllegalArgumentException("Peso entre 30 y 300 kg.");
		} catch (RuntimeException e) {
			error.accept(message(e, "Introduce tu peso en kg."));
			return;
		}
		busy = true;
		long token = generation;
		LocalDate day = clock.get();
		BodyProfile updated = body == null ? null : body.withWeight(kg);
		store.weight(owner, day, kg, new NutritionStore.Callback<Void>() {
			public void success(Void v) {
				if (token != generation)
					return;
				weights.put(day, kg);
				if (updated == null) {
					busy = false;
					status = "Peso anotado.";
					changed.run();
					return;
				}
				store.saveBody(owner, updated, new NutritionStore.Callback<Void>() {
					public void success(Void ignored) {
						if (token != generation)
							return;
						busy = false;
						body = updated;
						status = "Peso anotado y objetivos actualizados.";
						changed.run();
					}

					public void failure(Exception e) {
						fail(token, e);
					}
				});
			}

			public void failure(Exception e) {
				fail(token, e);
			}
		});
	}

	// ---- Escáner y alimentos ----

	/**
	 * Comida a la que se añadirá el alimento (botón «Añadir alimento» de cada comida del diario).
	 */
	private FoodEntry.Meal targetMeal;

	public void startManual() {
		if (busy) return;
		editing = null;
		foodRevision++;
		looking = false;
		product = null;
		foodDraft.clear();
		foodDraft.put("unit", "G");
		foodDraft.put("meal", (targetMeal == null ? defaultMeal() : targetMeal).name());
		status = "";
	}

	public void startManual(FoodEntry.Meal meal) {
		if (busy) return;
		targetMeal = meal;
		startManual();
	}

	/**
	 * Rellena el formulario con un alimento ya registrado: misma ficha, cantidad a confirmar.
	 */
	public void startFrom(FoodEntry recent) {
		if (busy) return;
		startManual();
		foodDraft.put("name", recent.name);
		foodDraft.put("barcode", recent.barcode);
		foodDraft.put("unit", recent.unit.name());
		foodDraft.put("amount", format(recent.amount));
		foodDraft.put("kcal100", format(recent.kcal100));
		foodDraft.put("protein100", format(recent.protein100));
		foodDraft.put("carbs100", format(recent.carbs100));
		foodDraft.put("fat100", format(recent.fat100));
	}

	/** Abre la misma entrada, conservando ID, día y fecha de alta para la sincronización. */
	public void edit(FoodEntry entry) {
		if (owner == null || busy || entry == null) return;
		FoodEntry current = null;
		for (FoodEntry value : entries) if (value.id.equals(entry.id)) { current = value; break; }
		if (current == null) return;
		startFrom(current);
		editing = current;
		foodDraft.put("meal", current.meal.name());
	}

	public boolean editingFood() { return editing != null; }

	/** Cancelar invalida los callbacks del escáner y nunca escribe en el diario. */
	public void cancelFood() {
		if (busy) return;
		startManual();
	}

	/**
	 * Alimentos distintos registrados recientemente (más reciente primero), para añadirlos de nuevo con un toque.
	 */
	public List<FoodEntry> recentFoods(int limit) {
		List<FoodEntry> ordered = new ArrayList<>(entries);
		ordered.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
		Map<String, FoodEntry> unique = new LinkedHashMap<>();
		for (FoodEntry e : ordered)
			unique.putIfAbsent(e.name.toLowerCase(Locale.ROOT) + "|" + e.kcal100, e);
		List<FoodEntry> result = new ArrayList<>(unique.values());
		return result.subList(0, Math.min(limit, result.size()));
	}

	/** Identifica el formulario y la cuenta que abren el escáner. */
	public long scanToken() {
		return foodRevision;
	}

	/**
	 * @return false si el contenido leído no es un código de alimento; en ese caso ya se ha informado del error.
	 */
	public boolean scanned(String code) {
		return scanned(code, scanToken());
	}

	/** Un resultado de cámara solo pertenece al formulario y cuenta que abrieron el escáner. */
	public boolean scanned(String code, long scanToken) {
		if (owner == null || scanToken != foodRevision)
			return false;
		if (code == null)
			return false;
		String digits = FoodProduct.extractCode(code);
		if (digits == null) {
			error.accept("El código leído no identifica un alimento. Escanea el código de barras (EAN/UPC) o un QR GS1 del envase.");
			return false;
		}
		looking = true;
		product = null;
		status = "Buscando " + digits + " en Open Food Facts…";
		changed.run();
		long token = generation;
		long request = ++foodRevision;
		lookup.find(digits, new NutritionStore.Callback<FoodProduct>() {
			public void success(FoodProduct found) {
				if (token != generation || request != foodRevision)
					return;
				looking = false;
				startManual();
				foodDraft.put("barcode", digits);
				if (found == null) {
					status = "Producto " + digits + " no encontrado. Copia los valores de la etiqueta.";
				} else {
					product = found;
					foodDraft.put("name", found.displayName());
					foodDraft.put("unit", found.unit.name());
					put("kcal100", found.kcal100);
					put("protein100", found.protein100);
					put("carbs100", found.carbs100);
					put("fat100", found.fat100);
					status = found.complete() ? "Ficha encontrada. Indica cuánto has tomado." :
						"Ficha incompleta: completa los valores que faltan con la etiqueta.";
				}
				changed.run();
			}

			public void failure(Exception e) {
				if (token != generation || request != foodRevision)
					return;
				looking = false;
				startManual();
				foodDraft.put("barcode", digits);
				status = "Sin conexión con Open Food Facts. Puedes introducir los valores de la etiqueta.";
				changed.run();
			}
		});
		return true;
	}

	private void put(String key, double value) {
		foodDraft.put(key, Double.isNaN(value) ? "" : format(value));
	}

	public FoodEntry.Meal defaultMeal() {
		int hour = java.time.LocalTime.now().getHour();
		if (hour < 11)
			return FoodEntry.Meal.BREAKFAST;
		if (hour < 16)
			return FoodEntry.Meal.LUNCH;
		if (hour < 20)
			return FoodEntry.Meal.SNACK;
		return FoodEntry.Meal.DINNER;
	}

	/**
	 * Vista previa del alimento con la cantidad escrita; null si aún falta algún dato.
	 */
	public FoodEntry preview() {
		try {
			return build();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private FoodEntry build() {
		FoodEntry value = FoodEntry.create(editing == null ? selected : editing.day, FoodEntry.Meal.valueOf(foodDraft.getOrDefault("meal", "OTHER")),
			foodDraft.getOrDefault("name", ""), foodDraft.get("barcode"),
			FoodEntry.Unit.valueOf(foodDraft.getOrDefault("unit", "G")), number(foodDraft.get("amount")),
			number(foodDraft.get("kcal100")), number(foodDraft.get("protein100")), number(foodDraft.get("carbs100")),
			number(foodDraft.get("fat100")));
		return editing == null ? value : new FoodEntry(editing.id, editing.day, value.meal, value.name, value.barcode,
			value.unit, value.amount, value.kcal100, value.protein100, value.carbs100, value.fat100, editing.createdAt);
	}

	public void addFood(Runnable done) {
		if (owner == null || busy)
			return;
		FoodEntry entry;
		try {
			entry = build();
		} catch (RuntimeException e) {
			error.accept("Revisa el alimento: " + message(e, "cantidad y valores por 100 son obligatorios."));
			return;
		}
		busy = true;
		long token = generation;
		loadRequest++;
		boolean update = editing != null;
		changed.run();
		NutritionStore.Callback<Void> callback = new NutritionStore.Callback<Void>() {
			public void success(Void v) {
				if (token != generation)
					return;
				busy = false;
				List<FoodEntry> next = new ArrayList<>(entries);
				if (update) next.removeIf(old -> old.id.equals(entry.id));
				next.add(entry);
				entries = next;
				dailyCalories.put(entry.day, totals(entry.day)[0]);
				product = null;
				foodDraft.clear();
				editing = null;
				foodRevision++;
				status = entry.name + (update ? " actualizado: " : " añadido: ") + Math.round(entry.kcal()) + " kcal.";
				done.run();
				changed.run();
			}

			public void failure(Exception e) {
				fail(token, e);
			}
		};
		if (update) store.update(owner, entry, callback);
		else store.add(owner, entry, callback);
	}

	public void remove(FoodEntry entry) {
		if (owner == null || busy)
			return;
		busy = true;
		long token = generation;
		loadRequest++;
		changed.run();
		store.remove(owner, entry.id, new NutritionStore.Callback<Void>() {
			public void success(Void v) {
				if (token != generation)
					return;
				busy = false;
				List<FoodEntry> next = new ArrayList<>(entries);
				next.removeIf(e -> e.id.equals(entry.id));
				entries = next;
				boolean dayHasEntries = false;
				for (FoodEntry e : next)
					if (e.day.equals(entry.day)) {
						dayHasEntries = true;
						break;
					}
				if (dayHasEntries)
					dailyCalories.put(entry.day, totals(entry.day)[0]);
				else
					dailyCalories.remove(entry.day);
				status = entry.name + " eliminado del diario.";
				changed.run();
			}

			public void failure(Exception e) {
				fail(token, e);
			}
		});
	}

	private void fail(long token, Exception e) {
		if (token != generation)
			return;
		busy = false;
		error.accept("No se pudo guardar: " + e.getMessage());
	}

	static double number(String text) {
		if (text == null || text.trim().isEmpty())
			throw new IllegalArgumentException("faltan valores numéricos.");
		String value = text.trim().replace(',', '.');
		if (!value.matches("\\d+(?:\\.\\d+)?"))
			throw new IllegalArgumentException("«" + text.trim() + "» no es un número válido.");
		return Double.parseDouble(value);
	}

	public static String format(double value) {
		if (value == Math.rint(value))
			return Long.toString((long) value);
		return String.format(Locale.ROOT, "%.1f", value).replace('.', ',');
	}

	private static String message(RuntimeException e, String fallback) {
		return e.getMessage() == null || e instanceof NumberFormatException ? fallback : e.getMessage();
	}
}
