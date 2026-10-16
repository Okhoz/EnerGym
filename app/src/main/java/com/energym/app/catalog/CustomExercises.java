package com.energym.app.catalog;

import com.energym.app.domain.*;
import com.energym.app.domain.RoutineRequest.Area;

import java.util.*;

/**
 * MODELO+CONTROLADOR de los ejercicios creados por la persona (como en FitNotes): nombre, tipo, grupo muscular y
 * material. Se guardan por cuenta en el móvil y se añaden al catálogo para usarlos en rutinas, entreno libre y
 * planes. Encajan con cualquier objetivo, lugar y disciplina; solo exigen el material elegido.
 */
public final class CustomExercises {
	public interface Store {
		/** Ejercicios guardados de esa cuenta (vacío si ninguno). */
		List<Exercise> load(String owner);

		void save(String owner, List<Exercise> exercises);
	}

	/** Marca del grupo muscular dentro de la descripción (el modelo de ejercicio no tiene un campo propio). */
	static final String AREA_MARK = "Grupo muscular: ";

	private final Store store;
	private final CatalogRepository catalog;
	private final Runnable changed;
	private String owner;
	private List<Exercise> mine = Collections.emptyList();

	public CustomExercises(Store store, CatalogRepository catalog, Runnable changed) {
		this.store = store;
		this.catalog = catalog;
		this.changed = changed;
	}

	public void activate(String next) {
		if (Objects.equals(owner, next))
			return;
		owner = next;
		mine = next == null ? Collections.emptyList() : new ArrayList<>(store.load(next));
		catalog.setCustom(mine);
	}

	public List<Exercise> mine() {
		return Collections.unmodifiableList(mine);
	}

	/** Crea un ejercicio; devuelve el mensaje de error o vacío si se guardó. */
	public String add(String name, MetricType type, Area area, Set<TrainingProfile.Resource> resources, String notes) {
		if (owner == null)
			return "Entra con tu cuenta para crear ejercicios.";
		String clean = name == null ? "" : name.trim();
		if (clean.length() < 2 || clean.length() > 60)
			return "Escribe un nombre de 2 a 60 caracteres.";
		for (Exercise e : catalog.getExercises())
			if (e.getName().equalsIgnoreCase(clean))
				return "Ya existe un ejercicio llamado «" + e.getName() + "».";
		Set<String> sports = new LinkedHashSet<>();
		for (Sport sport : catalog.getSports())
			sports.add(sport.getId());
		Exercise created = build("custom-" + UUID.randomUUID().toString().substring(0, 12), clean, type, area, resources,
			notes, sports);
		List<Exercise> next = new ArrayList<>(mine);
		next.add(created);
		persist(next);
		return "";
	}

	public void remove(String id) {
		if (owner == null)
			return;
		List<Exercise> next = new ArrayList<>(mine);
		next.removeIf(e -> e.getId().equals(id));
		persist(next);
	}

	private void persist(List<Exercise> next) {
		mine = next;
		store.save(owner, next);
		catalog.setCustom(next);
		changed.run();
	}

	static Exercise build(String id, String name, MetricType type, Area area, Set<TrainingProfile.Resource> resources,
		String notes, Set<String> sports) {
		String description = AREA_MARK + area.label + "." + (notes == null || notes.trim().isEmpty() ? "" :
			" " + notes.trim()) + " Ejercicio creado por ti.";
		return new Exercise(id, name, type, EnumSet.allOf(TrainingProfile.Goal.class),
			EnumSet.allOf(TrainingProfile.Environment.class), resources == null ? Collections.emptySet() : resources,
			sports, Exercise.Coverage.SPECIFIC, false, description, Collections.emptyList());
	}

	/** Grupo muscular elegido al crear el ejercicio (null si no es propio). */
	public static Area area(Exercise e) {
		if (!e.getId().startsWith("custom-") || !e.getDescription().startsWith(AREA_MARK))
			return null;
		String label = e.getDescription().substring(AREA_MARK.length()).split("\\.")[0];
		for (Area a : Area.values())
			if (a.label.equals(label))
				return a;
		return null;
	}
}
