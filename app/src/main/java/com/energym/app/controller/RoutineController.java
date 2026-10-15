package com.energym.app.controller;

import com.energym.app.domain.*;
import com.energym.app.assistant.ChatProtocol;

import java.util.*;
import java.util.function.*;

/**
 * CONTROLADOR: la vista solo presenta la propuesta y remite decisiones explícitas.
 */
public final class RoutineController {
	public Routine preview, accepted;
	public boolean busy;
	private final AppStore store;
	private final Supplier<TrainingProfile> profile;
	private final List<Exercise> catalog;
	private final Runnable changed;
	private final Consumer<String> error;
	private long generation;

	public RoutineController(AppStore store, Supplier<TrainingProfile> profile, List<Exercise> catalog, Runnable changed, Consumer<String> error) {
		this.store = store;
		this.profile = profile;
		this.catalog = catalog;
		this.changed = changed;
		this.error = error;
	}

	/**
	 * Crear no escribe: la propuesta solo se vuelve versión después de una aceptación explícita.
	 */
	public void create() {
		if (busy)
			return;
		if (profile.get() == null) {
			error.accept("Guarda tu perfil antes de crear una propuesta.");
			return;
		}
		preview = new RoutinePlanner().create(profile.get(), catalog);
		changed.run();
	}

	public void reject() {
		if (busy)
			return;
		preview = null;
		changed.run();
	}

	public boolean canAccept() {
		return !busy && preview != null && !preview.isEmpty();
	}

	public void accept() {
		if (busy)
			return;
		if (preview == null || !preview.isValidFor(previewProfile())) {
			error.accept("La propuesta está vacía o el perfil ha cambiado. Crea una nueva propuesta.");
			return;
		}
		busy = true;
		long token = generation;
		Routine proposal = preview;
		changed.run();
		AppStore.Callback<Routine> callback = new AppStore.Callback<Routine>() {
			public void success(Routine value) {
				if (token != generation)
					return;
				busy = false;
				accepted = value;
				preview = null;
				changed.run();
			}

			public void failure(Exception e) {
				if (token != generation)
					return;
				busy = false;
				error.accept(e.getMessage());
				changed.run();
			}
		};
		// Una rutina manual tiene contexto propio; las demás conservan la validación del perfil general.
		if (manual(proposal))
			store.acceptManual(proposal, callback);
		else
			store.accept(proposal, callback);
	}

	private static boolean manual(Routine value) {
		return value != null && value.getMetadata() != null &&
			value.getMetadata().getOrigin() == RoutineMetadata.Origin.MANUAL;
	}

	private TrainingProfile previewProfile() {
		return manual(preview) ? preview.getProfile() : profile.get();
	}

	/**
	 * Invalida callbacks al cambiar de cuenta; una respuesta antigua no puede repoblar la vista.
	 */
	public void reset() {
		generation++;
		preview = null;
		accepted = null;
		busy = false;
	}

	public List<Exercise> alternatives(int index) {
		if (preview == null || index < 0 || index >= preview.getItems().size())
			return Collections.emptyList();
		List<Exercise> result = new ArrayList<>(new CompatibilityEngine().alternatives(preview.getItems().get(index).getExercise(), previewProfile(), catalog));
		Set<String> used = new HashSet<>();
		for (RoutineItem item : preview.getItems())
			used.add(item.getExercise().getId());
		result.removeIf(e -> used.contains(e.getId()));
		return Collections.unmodifiableList(result);
	}

	public void substitute(int index, String exerciseId) {
		if (busy)
			return;
		for (Exercise e : alternatives(index))
			if (e.getId().equals(exerciseId)) {
				List<RoutineItem> items = new ArrayList<>(preview.getItems());
				RoutineItem old = items.get(index);
				Map<String, String> prescription = new LinkedHashMap<>(old.getPrescription());
				if (e.getMetricType() == MetricType.STRENGTH && Collections.disjoint(e.getRequiredResources(),
					EnumSet.of(TrainingProfile.Resource.DUMBBELLS, TrainingProfile.Resource.BARBELL,
						TrainingProfile.Resource.MACHINE)))
					prescription.put("loadKg", "0");
				try {
					ChatProtocol.validatePrescription(e, prescription, old.getEstimatedMinutes());
				} catch (IllegalArgumentException invalid) {
					error.accept("La prescripción no es compatible con la sustitución. Revisa el ejercicio y su duración.");
					return;
				}
				items.set(index, new RoutineItem(e, prescription, old.getEstimatedMinutes(), "Sustitución compatible con el mismo objetivo, métrica, recursos y cobertura."));
				preview = new Routine(UUID.randomUUID().toString(), 0, preview.getTitle(), preview.getProfile(), items, preview.getExplanation(), preview.getMetadata());
				changed.run();
				return;
			}
		error.accept("No hay una sustitución compatible disponible.");
	}
}
