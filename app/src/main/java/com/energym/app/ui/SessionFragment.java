package com.energym.app.ui;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.domain.*;

import android.os.*;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.*;

/**
 * VISTA de registro: el esquema viene de MetricValidator; no asume kg/repeticiones para todas las actividades.
 */
public final class SessionFragment extends ScreenFragment {
	/** Fin del descanso en curso (reloj del sistema); sobrevive a los repintados y al giro de pantalla. */
	private static long restEndsAt;
	private final Handler ticker = new Handler(Looper.getMainLooper());
	private TextView restLabel;
	private final Runnable tick = this::tick;
	/** Ejercicio y registros mostrados en el último pintado: detectan el avance tras guardar. */
	private int shownExercise = -1, shownResults = -1;
	/** Registro de fuerza: ejercicio mostrado, pestaña, serie en edición y valores de − / +. */
	private String trackKey = "", kgInput = "", repsInput = "", kindInput = "N", rpeInput = "";
	private static final List<String> RPE = Arrays.asList("", "6", "7", "7.5", "8", "8.5", "9", "9.5", "10");
	private int trackTab, selectedSet = -1;
	/** Unidad de peso elegida en Cuenta: se muestra y escribe en ella, se guarda siempre en kg. */
	private boolean lb;
	/** «Más opciones» del registro desplegado. */
	private boolean moreOptions;
	/** Lista de ejercicios del día desplegada. */
	private boolean showList;

	public SessionFragment() {
		super(R.layout.fragment_session);
	}

	@Override
	public void onResume() {
		super.onResume();
		com.energym.app.reminders.RestTimer.inFront(true);
	}

	@Override
	public void onPause() {
		com.energym.app.reminders.RestTimer.inFront(false);
		super.onPause();
	}

	@Override
	public void onDestroyView() {
		ticker.removeCallbacks(tick);
		restLabel = null;
		super.onDestroyView();
	}

	protected void content() {
		heading("", Txt.t(R.string.ses_title), app.sessions.session == null ? "" : app.sessions.session.getRoutine().getTitle());
		if (app.sessions.session == null) {
			card(Txt.t(app.sessions.busy ? R.string.ses_opening : R.string.ses_none),
				Txt.t(app.sessions.busy ? R.string.ses_opening_body : R.string.ses_none_body));
			return;
		}
		if (app.sessions.session.getStatus() == Session.Status.FINISHED) {
			card(Txt.t(R.string.ses_finished), Txt.t(R.string.ses_finished_body));
			return;
		}
		List<String> ids = new ArrayList<>(), names = new ArrayList<>();
		Set<Integer> links = links();
		int i = 0;
		for (RoutineItem item : app.sessions.session.getRoutine().getItems()) {
			String group = Supersets.label(links, i);
			ids.add(Integer.toString(i++));
			// ✓ marca lo ya apuntado para ver de un vistazo qué falta; A1/A2 las superseries.
			names.add((group.isEmpty() ? "" : group + " · ") + item.getExercise().getName());
		}
		int saved = app.sessions.session.getResults().size();
		exercises(names, links, saved);
		if (shownResults >= 0 && saved > shownResults &&
			app.sessions.item().getExercise().getMetricType() != MetricType.STRENGTH)
			android.widget.Toast.makeText(requireContext(), Txt.t(R.string.ses_exercise_saved), android.widget.Toast.LENGTH_SHORT).show();
		// Al cambiar de ejercicio (a mano o tras guardar) se vuelve arriba para verlo desde el principio.
		if (shownExercise >= 0 && shownExercise != app.sessions.selected && getView() instanceof android.widget.ScrollView)
			getView().post(() -> {
				if (getView() != null)
					((android.widget.ScrollView) getView()).smoothScrollTo(0, 0);
			});
		shownExercise = app.sessions.selected;
		shownResults = saved;
		RoutineItem item = app.sessions.item();
		Exercise e = item.getExercise();
		if (e.getMetricType() == MetricType.STRENGTH)
			tracker(item, e);
		else
			form(item, e);
		if (FreeWorkout.is(app.sessions.session.getRoutine())) {
			com.google.android.material.button.MaterialButton more = action(rows, Txt.t(R.string.ses_add_exercise_button),
				!app.sessions.busy && !app.trainingMutation,
				() -> ExercisePicker.show(requireContext(), Txt.t(R.string.ses_add_exercise), app.freeCandidates(), app::addFreeExercise));
			more.setTag("session_add_exercise");
		}
		if (e.getMetricType() != MetricType.STRENGTH)
			rest();
		add(rows, new android.view.View(requireContext()), 24);
		boolean done = saved >= ids.size();
		LinearLayout finish = card(Txt.t(done ? R.string.ses_all_logged : R.string.ses_finish_title), done ?
			Txt.t(R.string.ses_all_logged_body) : Txt.t(R.string.ses_logged_of_total, saved, ids.size()));
		com.google.android.material.button.MaterialButton end =
			action(finish, Txt.t(R.string.ses_finish), !app.sessions.busy, app.sessions::finish);
		if (done)
			primary(end);
		action(finish, Txt.t(R.string.ses_discard), !app.sessions.busy && !app.trainingMutation, () -> {
			Session current = app.sessions.session;
			long scope = app.accountRevision();
			new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle(Txt.t(R.string.ses_discard_title))
				.setMessage(Txt.t(R.string.ses_discard_message))
				.setNegativeButton(Txt.t(R.string.common_cancel), null)
				.setPositiveButton(Txt.t(R.string.ses_discard_confirm), (dialog, button) -> {
					if (scope == app.accountRevision()) app.deleteSession(current);
				}).show();
		});
	}

	/**
	 * Navegación entre ejercicios: ‹ › para el anterior y el siguiente y la lista del día (lo hecho en cada uno) al
	 * desplegarla.
	 */
	private void exercises(List<String> names, Set<Integer> links, int saved) {
		int at = app.sessions.selected, count = names.size();
		section(Txt.t(R.string.ses_position, at + 1, count, saved), 8);
		LinearLayout nav = columns(rows, 0);
		com.google.android.material.button.MaterialButton back =
			compact(nav, "‹", at > 0 && !app.sessions.busy, () -> app.sessions.select(at - 1));
		back.setContentDescription(Txt.t(R.string.ses_previous));
		compact(nav, Txt.t(showList ? R.string.ses_hide_list : R.string.ses_all), true, () -> {
			showList = !showList;
			render();
		}).setTag("session_list_toggle");
		com.google.android.material.button.MaterialButton forward =
			compact(nav, "›", at + 1 < count && !app.sessions.busy, () -> app.sessions.select(at + 1));
		forward.setContentDescription(Txt.t(R.string.ses_next));
		add(rows, new android.view.View(requireContext()), 16);
		if (!showList)
			return;
		LinearLayout list = card(Txt.t(R.string.ses_today_list), Txt.t(R.string.ses_today_list_body));
		List<RoutineItem> items = app.sessions.session.getRoutine().getItems();
		for (int i = 0; i < items.size(); i++) {
			Exercise x = items.get(i).getExercise();
			Map<String, String> values = app.sessions.session.getResults().get(x.getId());
			List<SetLog.Entry> done = x.getMetricType() == MetricType.STRENGTH ? SetLog.of(values) :
				Collections.emptyList();
			String detail = values == null ? Txt.t(R.string.ses_pending, planned(items.get(i))) :
				x.getMetricType() != MetricType.STRENGTH || done.isEmpty() ? Txt.t(R.string.ses_logged) :
					Txt.t(R.string.ses_sets_of_total, done.size(), items.get(i).getPrescription().getOrDefault("sets", "?"),
						compactSets(done));
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, list, false);
			((TextView) row.findViewById(R.id.food_name)).setText((i + 1) + ".  " + names.get(i));
			((TextView) row.findViewById(R.id.food_detail)).setText(detail);
			((TextView) row.findViewById(R.id.food_kcal)).setText(i == at ? "▶" : values != null ? "✓" : "");
			if (i == at)
				row.setBackground(rounded(R.color.surface_selected, 12, R.color.gold, 1));
			int index = i;
			row.setTag("session_exercise_" + i);
			row.setContentDescription(names.get(i) + ". " + detail + ". " + Txt.t(i == at ? R.string.ses_current : R.string.ses_tap_to_go));
			row.setOnClickListener(v -> {
				showList = false;
				if (index == app.sessions.selected)
					render();
				else
					app.sessions.select(index);
			});
			list.addView(row);
		}
	}

	/** Formulario de siempre para lo que no se mide en series de peso × repeticiones (cardio, movilidad…). */
	private void form(RoutineItem item, Exercise e) {
		LinearLayout context = card(e.getName(),
			Txt.t(R.string.ses_planned) + "\n" + AppController.prescription(e.getMetricType(), item.getPrescription()) +
				"\n\n" + Txt.t(app.sessions.session.getResults().containsKey(e.getId()) ?
				R.string.ses_already_logged : R.string.ses_do_it));
		Session last = lastTime(e.getId());
		if (last != null)
			text(context, Txt.t(R.string.ses_last_time, day(last),
				AppController.metrics(e.getMetricType(), last.getResults().get(e.getId()))), R.style.EnerGym_Body, 12);
		action(context, Txt.t(R.string.ses_see_technique_named, e.getName()), () -> app.openExercise(e));
		LinearLayout fields = card(Txt.t(R.string.ses_what_you_did), Txt.t(R.string.ses_what_you_did_body));
		Map<String, String> inputs = app.sessions.inputs();
		for (MetricValidator.Field f : new MetricValidator().fields(e.getMetricType()))
			if (f.choices.isEmpty())
				textField(fields, f.key, f.label, inputs.getOrDefault(f.key, ""), true, f.integer,
					v -> app.sessions.input(f.key, v));
			else
				choice(fields, f.key, f.label, f.choices, choiceLabels(f.choices), inputs.getOrDefault(f.key, ""),
					v -> app.sessions.input(f.key, v));
		primary(action(rows, Txt.t(R.string.ses_save_exercise), !app.sessions.busy, app.sessions::record));
	}

	/**
	 * Registro estilo FitNotes para fuerza: pestañas Registrar / Historial / Gráfica, peso y repeticiones con − y +,
	 * cada serie se guarda al momento y las que baten tu récord llevan 🏆.
	 */
	private void tracker(RoutineItem item, Exercise e) {
		lb = Units.lb(requireContext());
		String key = app.sessions.session.getSyncId() + "/" + e.getId() + "/" + lb;
		if (!key.equals(trackKey)) {
			trackKey = key;
			selectedSet = -1;
			// Lo escrito en − / + vive en el controlador: sobrevive a girar la pantalla y a salir y volver.
			Map<String, String> typed = app.sessions.inputs();
			seedInputs(item, e);
			if (Plates.unit(lb).equals(typed.get("trackUnit")))
				kgInput = typed.getOrDefault("trackKg", kgInput);
			repsInput = typed.getOrDefault("trackReps", repsInput);
			kindInput = typed.getOrDefault("trackKind", "N");
			rpeInput = typed.getOrDefault("trackRpe", "");
		}
		segmented("track", new String[]{Txt.t(R.string.ses_tab_log), Txt.t(R.string.ses_tab_history), Txt.t(R.string.ses_tab_graph)},
			new String[]{"track_tab_log", "track_tab_history", "track_tab_graph"}, trackTab, index -> {
				trackTab = index;
				render();
			});
		if (trackTab == 1) {
			history(e);
			rest();
			return;
		}
		if (trackTab == 2) {
			graph(e);
			rest();
			return;
		}
		List<SetLog.Entry> sets = app.sessions.sets();
		boolean editing = selectedSet >= 0 && selectedSet < sets.size();
		Session last = lastTime(e.getId());
		List<SetLog.Entry> before = last == null ? Collections.<SetLog.Entry>emptyList() :
			SetLog.of(last.getResults().get(e.getId()));
		LinearLayout log = card(e.getName() + " · " + (editing ? Txt.t(R.string.ses_editing_set, selectedSet + 1) :
			Txt.t(R.string.ses_set_number, sets.size() + 1)), Txt.t(R.string.ses_today_target, planned(item)) +
			(before.isEmpty() ? "" : "\n" + Txt.t(R.string.ses_last_time, day(last), compactSets(before))) +
			(last == null || last.getResults().get(e.getId()).getOrDefault(SetLog.NOTE, "").isEmpty() ? "" :
				"\n📝 " + last.getResults().get(e.getId()).get(SetLog.NOTE)));
		stepper(log, Txt.t(R.string.ses_weight_unit, Plates.unit(lb)), "track_kg", kgInput, lb ? 5 : 2.5, true, v -> {
			kgInput = v;
			app.sessions.input("trackKg", v);
			app.sessions.input("trackUnit", Plates.unit(lb));
		});
		stepper(log, Txt.t(R.string.ses_reps), "track_reps", repsInput, 1, false, v -> {
			repsInput = v;
			app.sessions.input("trackReps", v);
		});
		// Solo la primera vez con este ejercicio: luego la ayuda sobra.
		if (sets.isEmpty() && before.isEmpty())
			text(log, Txt.t(R.string.ses_tracker_help), R.style.EnerGym_FieldCaption, 12);
		// Consejo del entrenador para la siguiente serie según el RPE de la última (objetivo RPE 8).
		if (!editing && !sets.isEmpty()) {
			Autoregulation.Advice advice = Autoregulation.next(sets.get(sets.size() - 1), lb ? Plates.toKg(5, true) : 2.5);
			if (advice != null) {
				String target = advice.kg > 0 ? SetLog.format(Plates.toUnit(advice.kg, lb)) : "";
				boolean differs = advice.kg > 0 ? !target.equals(kgInput) : !Integer.toString(advice.reps).equals(repsInput);
				text(log, "🧠 " + (lb && advice.kg > 0 ? advice.text.replaceAll("[0-9]+(,[0-9]+)? kg",
					target.replace('.', ',') + " lb") : advice.text), R.style.EnerGym_Body, 12).setTag("track_advice");
				if (differs)
					action(log, advice.kg > 0 ? Txt.t(R.string.ses_use_weight, target.replace('.', ','), Plates.unit(lb)) :
						Txt.t(R.string.ses_use_reps, advice.reps), true, () -> {
						if (advice.kg > 0) {
							kgInput = target;
							app.sessions.input("trackKg", target);
							app.sessions.input("trackUnit", Plates.unit(lb));
						} else {
							repsInput = Integer.toString(advice.reps);
							app.sessions.input("trackReps", repsInput);
						}
						render();
					}).setTag("track_use_advice");
			}
		}
		LinearLayout buttons = columns(log, 16);
		com.google.android.material.button.MaterialButton save =
			compact(buttons, Txt.t(editing ? R.string.ses_update : R.string.ses_save_set), !app.sessions.busy, () -> {
				double typedKg = number(kgInput, -1);
				double kg = typedKg < 0 ? typedKg : Plates.toKg(typedKg, lb);
				int reps = (int) number(repsInput, 0);
				SetLog.Kind kind = SetLog.Kind.valueOf(kindName(kindInput));
				double rpe = number(rpeInput, 0);
				if (editing)
					app.sessions.updateSet(selectedSet, kg, reps, kind, rpe);
				else
					saveSet(item, kg, reps, kind, rpe);
				selectedSet = -1;
			});
		primary(save);
		compact(buttons, Txt.t(editing ? R.string.common_remove : R.string.common_delete), !app.sessions.busy, () -> {
			if (editing) {
				app.sessions.removeSet(selectedSet);
				selectedSet = -1;
			} else {
				kgInput = "";
				repsInput = "";
				render();
			}
		});
		// Lo más habitual, a un toque: otra serie igual que la última.
		if (!editing && !sets.isEmpty()) {
			SetLog.Entry previous = sets.get(sets.size() - 1);
			action(log, Txt.t(R.string.ses_repeat, weight(previous.kg).toLowerCase(Locale.ROOT), previous.reps),
				!app.sessions.busy, () -> saveSet(item, previous.kg, previous.reps, previous.kind, previous.rpe))
				.setTag("track_repeat");
		}
		if (editing)
			action(log, Txt.t(R.string.ses_cancel_edit), true, () -> {
				selectedSet = -1;
				render();
			});
		// Tipo de serie, RPE, nota, técnica, discos y superserie: plegados para que registrar sea rápido.
		List<String> chosen = new ArrayList<>();
		if (!"N".equals(kindInput))
			chosen.add(SetLog.Kind.valueOf(kindName(kindInput)).display());
		if (!rpeInput.isEmpty())
			chosen.add("RPE " + rpeInput.replace('.', ','));
		action(log, Txt.t(moreOptions ? R.string.ses_less_options : R.string.ses_more_options) +
			(chosen.isEmpty() ? "" : " · " + String.join(" · ", chosen)), true, () -> {
			moreOptions = !moreOptions;
			render();
		}).setTag("track_more");
		if (moreOptions)
			options(log, item, e, sets, editing);
		rest();
		if (sets.isEmpty())
			return;
		double record = previousBest(e.getId());
		int repsRecord = previousBestReps(e.getId());
		LinearLayout list = card(Txt.n(R.plurals.ses_today_sets, sets.size()), Txt.t(R.string.ses_tap_set));
		for (int i = 0; i < sets.size(); i++) {
			SetLog.Entry s = sets.get(i);
			// Récord: más 1RM estimado con carga, o más repeticiones que nunca a peso corporal.
			boolean pr = s.counts() && (s.kg > 0 ? s.oneRepMax() > record + 0.001 : s.reps > repsRecord);
			if (pr && s.kg > 0)
				record = s.oneRepMax();
			else if (pr)
				repsRecord = s.reps;
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, list, false);
			((TextView) row.findViewById(R.id.food_name)).setText((i + 1) + ".   " + weight(s.kg) + " × " +
				Txt.n(R.plurals.ses_reps_count, s.reps));
			((TextView) row.findViewById(R.id.food_detail)).setText(tags(s, pr ? Txt.t(R.string.ses_new_pr) :
				s.kg > 0 && s.counts() ? Txt.t(R.string.ses_estimated_1rm, weight(Math.round(s.oneRepMax() * 2) / 2.0)) : ""));
			((TextView) row.findViewById(R.id.food_kcal)).setText(pr ? "🏆" : "");
			int index = i;
			row.setTag("track_set_" + i);
			row.setContentDescription(Txt.t(R.string.ses_set_description, i + 1, weight(s.kg), s.reps) +
				(s.kind != SetLog.Kind.NORMAL ? ", " + s.kind.display() : "") + (s.rpe > 0 ? ", RPE " + SetLog.format(s.rpe) : "") +
				(pr ? ", " + Txt.t(R.string.ses_pr_lower) : "") + ". " + Txt.t(R.string.ses_tap_to_edit));
			if (index == selectedSet)
				row.setBackground(rounded(R.color.surface_selected, 12, R.color.gold, 1));
			row.setOnClickListener(v -> {
				selectedSet = index;
				kgInput = SetLog.format(Plates.toUnit(s.kg, lb));
				repsInput = Integer.toString(s.reps);
				kindInput = Character.toString(s.kind.code);
				rpeInput = s.rpe > 0 ? SetLog.format(s.rpe) : "";
				render();
			});
			list.addView(row);
		}
	}

	/** Superseries de este entreno (índices enlazados con el siguiente), guardadas en este móvil. */
	private Set<Integer> links() {
		if (app.sessions.session == null)
			return Collections.emptySet();
		return Supersets.decode(requireContext().getSharedPreferences("supersets", android.content.Context.MODE_PRIVATE)
			.getString(app.sessions.session.getSyncId(), ""), app.sessions.session.getRoutine().getItems().size());
	}

	private void saveLinks(Set<Integer> links) {
		requireContext().getSharedPreferences("supersets", android.content.Context.MODE_PRIVATE).edit()
			.putString(app.sessions.session.getSyncId(), Supersets.encode(links)).apply();
		if (state.extras != null)
			state.extras.push("supersets", app.sessions.session.getSyncId(), Supersets.encode(links));
	}

	/** «Calentamiento · RPE 8 · detalle»: tipo y esfuerzo de una serie delante del detalle que ya hubiera. */
	private static String tags(SetLog.Entry s, String detail) {
		List<String> parts = new ArrayList<>();
		if (s.kind != SetLog.Kind.NORMAL)
			parts.add(s.kind.display());
		if (s.rpe > 0)
			parts.add("RPE " + SetLog.format(s.rpe).replace('.', ','));
		if (!detail.isEmpty())
			parts.add(detail);
		return String.join(" · ", parts);
	}

	private static String kindName(String code) {
		for (SetLog.Kind k : SetLog.Kind.values())
			if (code != null && code.length() == 1 && k.code == code.charAt(0))
				return k.name();
		return SetLog.Kind.NORMAL.name();
	}

	/** Guarda una serie nueva; en superserie o circuito pasa al siguiente sin descanso y descansa al cerrar la vuelta. */
	private void saveSet(RoutineItem item, double kg, int reps, SetLog.Kind kind, double rpe) {
		int from = app.sessions.selected;
		// Cada serie se valora por separado: el RPE no se arrastra a la siguiente.
		rpeInput = "";
		app.sessions.input("trackRpe", "");
		Set<Integer> group = links();
		app.sessions.addSet(kg, reps, kind, rpe, () -> {
			int to = Supersets.after(group, from);
			if (to != from && app.sessions.selected == from)
				app.sessions.select(to);
		});
		if (Supersets.restsAfter(group, from))
			startRest(item);
	}

	/** Lo que no hace falta en cada serie: tipo, RPE, nota, técnica, discos y superserie. */
	private void options(LinearLayout log, RoutineItem item, Exercise e, List<SetLog.Entry> sets, boolean editing) {
		List<String> kinds = new ArrayList<>(), kindNames = new ArrayList<>();
		for (SetLog.Kind k : SetLog.Kind.values()) {
			kinds.add(Character.toString(k.code));
			kindNames.add(k == SetLog.Kind.NORMAL ? Txt.t(R.string.ses_kind_normal) : k.display());
		}
		add(log, new android.view.View(requireContext()), 8);
		choice(log, "track_kind", Txt.t(R.string.ses_set_type), kinds, kindNames, kindInput, v -> {
			kindInput = v;
			app.sessions.input("trackKind", v);
		});
		List<String> rpeNames = new ArrayList<>();
		for (String r : RPE)
			rpeNames.add(r.isEmpty() ? Txt.t(R.string.ses_rpe_none) : "RPE " + r.replace('.', ',') + (r.equals("10") ?
				Txt.t(R.string.ses_rpe_10) : r.equals("8") ? Txt.t(R.string.ses_rpe_8) : r.equals("9") ? Txt.t(R.string.ses_rpe_9) : ""));
		choice(log, "track_rpe", Txt.t(R.string.ses_rpe_label), RPE, rpeNames, rpeInput, v -> {
			rpeInput = v;
			app.sessions.input("trackRpe", v);
		});
		textField(log, "track_note", Txt.t(R.string.ses_note_label), app.sessions.note(), false, false,
			Txt.t(R.string.ses_note_hint), Txt.t(R.string.ses_note_helper),
			v -> app.sessions.input(SetLog.NOTE, v));
		if (!sets.isEmpty())
			action(log, Txt.t(R.string.ses_save_note), !app.sessions.busy, () -> {
				app.sessions.saveNote(app.sessions.inputs().getOrDefault(SetLog.NOTE, ""));
				android.widget.Toast.makeText(requireContext(), Txt.t(R.string.ses_note_saved), android.widget.Toast.LENGTH_SHORT).show();
			});
		com.google.android.material.button.MaterialButton technique =
			action(log, Txt.t(R.string.ses_see_technique), true, () -> app.openExercise(e));
		technique.setContentDescription(Txt.t(R.string.ses_see_technique_named, e.getName()));
		double best = Math.max(previousBest(e.getId()), SetLog.best(sets));
		// Sin carga (peso corporal) no hay discos que calcular ni 1RM en kg.
		if (best > 0 || number(kgInput, 0) > 0)
			action(log, Txt.t(R.string.ses_plates), true, () -> PlateDialog.show(requireContext(),
				Plates.toKg(number(kgInput, 0), lb), best, lb)).setTag("track_plates");
		int count = app.sessions.session.getRoutine().getItems().size();
		int at = app.sessions.selected;
		if (!editing && at + 1 < count) {
			Set<Integer> links = links();
			String next = app.sessions.session.getRoutine().getItems().get(at + 1).getExercise().getName();
			action(log, Txt.t(links.contains(at) ? R.string.ses_superset_remove : R.string.ses_superset_add, next),
				!app.sessions.busy, () -> {
					Set<Integer> changed = new TreeSet<>(links);
					if (!changed.remove(at))
						changed.add(at);
					saveLinks(changed);
					android.widget.Toast.makeText(requireContext(), changed.contains(at) ?
						Txt.t(R.string.ses_superset_on, next) : Txt.t(R.string.ses_superset_off),
						android.widget.Toast.LENGTH_SHORT).show();
					render();
				}).setTag("track_superset");
		}
	}

	/** Valores de partida: la última serie de hoy, si no la última vez que lo hiciste, si no lo previsto. */
	private void seedInputs(RoutineItem item, Exercise e) {
		List<SetLog.Entry> today = app.sessions.sets();
		Session last = lastTime(e.getId());
		List<SetLog.Entry> before = last == null ? Collections.emptyList() : SetLog.of(last.getResults().get(e.getId()));
		// Hoy se parte del objetivo que fija la progresión automática; tras una serie, de la última apuntada.
		SetLog.Entry seed = !today.isEmpty() ? today.get(today.size() - 1) : null;
		String target = item.getPrescription().getOrDefault("loadKg", "0");
		if (seed == null && number(target, 0) == 0 && !before.isEmpty() && before.get(0).kg > 0)
			target = SetLog.format(before.get(0).kg);
		kgInput = SetLog.format(Plates.toUnit(seed != null ? seed.kg : number(target, 0), lb));
		repsInput = seed != null ? Integer.toString(seed.reps) : item.getPrescription().getOrDefault("repetitions", "8");
	}

	/** Fila «− valor +» para ajustar con toques o escribir el número. */
	private void stepper(LinearLayout target, String label, String tag, String value, double step, boolean decimal,
		java.util.function.Consumer<String> change) {
		text(target, label, R.style.EnerGym_FieldCaption, 12);
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(android.view.Gravity.CENTER_VERTICAL);
		android.widget.EditText field = new android.widget.EditText(requireContext());
		field.setTag(tag);
		field.setText(value);
		field.setGravity(android.view.Gravity.CENTER);
		field.setTextSize(28);
		field.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white));
		field.setBackground(rounded(R.color.surface, 16, R.color.text_secondary, 0));
		field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
			(decimal ? android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
		field.setContentDescription(label);
		field.setSelectAllOnFocus(true);
		field.addTextChangedListener(new android.text.TextWatcher() {
			public void beforeTextChanged(CharSequence s, int a, int b, int c) {
			}

			public void onTextChanged(CharSequence s, int a, int b, int c) {
			}

			public void afterTextChanged(android.text.Editable s) {
				change.accept(s.toString());
			}
		});
		row.addView(stepButton("−", Txt.t(R.string.ses_less, label.toLowerCase(Locale.ROOT)), () -> {
			double next = Math.max(0, number(field.getText().toString(), 0) - step);
			field.setText(decimal ? SetLog.format(next) : Integer.toString((int) Math.max(1, next)));
		}), new LinearLayout.LayoutParams(dp(64), dp(64)));
		LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, dp(64), 1);
		middle.setMargins(dp(10), 0, dp(10), 0);
		row.addView(field, middle);
		row.addView(stepButton("+", Txt.t(R.string.ses_more, label.toLowerCase(Locale.ROOT)), () -> {
			double next = number(field.getText().toString(), 0) + step;
			field.setText(decimal ? SetLog.format(next) : Integer.toString((int) next));
		}), new LinearLayout.LayoutParams(dp(64), dp(64)));
		add(target, row, 6);
	}

	private com.google.android.material.button.MaterialButton stepButton(String symbol, String description, Runnable click) {
		com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(
			requireContext(), null, com.google.android.material.R.attr.materialIconButtonFilledStyle);
		b.setText(symbol);
		b.setTextSize(26);
		b.setPadding(0, 0, 0, 0);
		b.setInsetTop(0);
		b.setInsetBottom(0);
		b.setCornerRadius(dp(16));
		b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
			androidx.core.content.ContextCompat.getColor(requireContext(), R.color.surface_selected)));
		b.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.gold));
		b.setContentDescription(description);
		b.setOnClickListener(v -> click.run());
		return b;
	}

	/** Historial del ejercicio: cada día que lo hiciste, con sus series y récords. */
	private void history(Exercise e) {
		List<Session> done = finishedWith(e.getId());
		if (done.isEmpty()) {
			card(Txt.t(R.string.ses_no_history), Txt.t(R.string.ses_no_history_body));
			return;
		}
		Collections.reverse(done);
		double best = 0;
		for (Session s : finishedWith(e.getId()))
			best = Math.max(best, SetLog.best(SetLog.of(s.getResults().get(e.getId()))));
		for (Session s : done.subList(0, Math.min(15, done.size()))) {
			List<SetLog.Entry> sets = SetLog.of(s.getResults().get(e.getId()));
			StringBuilder lines = new StringBuilder();
			for (int i = 0; i < sets.size(); i++) {
				SetLog.Entry x = sets.get(i);
				String extra = tags(x, "");
				lines.append(i == 0 ? "" : "\n").append(i + 1).append(".  ").append(weight(x.kg)).append(" × ")
					.append(x.reps).append(extra.isEmpty() ? "" : "  (" + extra + ")")
					.append(x.counts() && x.kg > 0 && Math.abs(x.oneRepMax() - best) < 0.001 ? "  🏆" : "");
			}
			String note = s.getResults().get(e.getId()).getOrDefault(SetLog.NOTE, "");
			if (!note.isEmpty())
				lines.append("\n📝 ").append(note);
			card(dayLong(s), lines.length() == 0 ? Txt.t(R.string.ses_no_sets) : lines.toString());
		}
	}

	/** Gráfica del 1RM estimado de cada día (o del peso máximo si es a peso corporal, de las repeticiones). */
	private void graph(Exercise e) {
		List<Session> done = finishedWith(e.getId());
		if (done.size() < 2) {
			card(Txt.t(R.string.ses_no_graph), Txt.t(R.string.ses_no_graph_body));
			return;
		}
		List<Session> recent = done.subList(Math.max(0, done.size() - 12), done.size());
		boolean bodyweight = true;
		for (Session s : recent)
			for (SetLog.Entry x : SetLog.of(s.getResults().get(e.getId())))
				if (x.kg > 0)
					bodyweight = false;
		double[] values = new double[recent.size()];
		String[] labels = new String[recent.size()];
		double top = 0;
		for (int i = 0; i < recent.size(); i++) {
			List<SetLog.Entry> sets = SetLog.of(recent.get(i).getResults().get(e.getId()));
			double v = 0;
			for (SetLog.Entry x : sets)
				v = Math.max(v, bodyweight ? x.reps : Plates.toUnit(x.oneRepMax(), lb));
			values[i] = Math.round(v * 10) / 10.0;
			labels[i] = day(recent.get(i));
			top = Math.max(top, values[i]);
		}
		String metric = Txt.t(bodyweight ? R.string.ses_max_reps : R.string.ses_1rm);
		LinearLayout card = card(metric, Txt.t(bodyweight ? R.string.ses_max_reps_body : R.string.ses_1rm_body));
		add(card, new com.energym.app.ui.charts.LineChartView(requireContext()), 12).set(values, labels,
			bodyweight ? "reps" : Plates.unit(lb), Txt.t(R.string.ses_metric_of, metric, e.getName()));
		text(card, Txt.t(R.string.ses_best, SetLog.format(top) + " " + (bodyweight ? Txt.t(R.string.ses_reps_lower) : Plates.unit(lb))),
			R.style.EnerGym_Body, 12);
	}

	/** Al guardar una serie arranca solo el descanso previsto, como el temporizador de FitNotes. */
	private void startRest(RoutineItem item) {
		double seconds = number(item.getPrescription().getOrDefault("restSeconds", "60"), 60);
		if (seconds > 0)
			restEndsAt(SystemClock.elapsedRealtime() + (long) (seconds * 1000), item.getExercise().getName());
	}

	/** Cambia el descanso en pantalla y su aviso fuera de la app (0 = parado). */
	private void restEndsAt(long at, String exercise) {
		restEndsAt = at;
		if (at == 0)
			com.energym.app.reminders.RestTimer.cancel(requireContext());
		else
			com.energym.app.reminders.RestTimer.start(requireContext(), at, exercise);
	}

	private List<Session> finishedWith(String exerciseId) {
		List<Session> result = new ArrayList<>();
		for (Session s : app.history)
			if (s.getStatus() == Session.Status.FINISHED && s.getResults().containsKey(exerciseId))
				result.add(s);
		result.sort(Comparator.comparingLong(Session::getCompletedAt));
		return result;
	}

	private double previousBest(String exerciseId) {
		double best = 0;
		for (Session s : finishedWith(exerciseId))
			best = Math.max(best, SetLog.best(SetLog.of(s.getResults().get(exerciseId))));
		return best;
	}

	/** Más repeticiones hechas en una serie sin carga (0 si nunca). */
	private int previousBestReps(String exerciseId) {
		int best = 0;
		for (Session s : finishedWith(exerciseId))
			for (SetLog.Entry x : SetLog.of(s.getResults().get(exerciseId)))
				if (x.kg == 0)
					best = Math.max(best, x.reps);
		return best;
	}

	private String planned(RoutineItem item) {
		Map<String, String> p = item.getPrescription();
		String load = p.getOrDefault("loadKg", "0");
		return p.getOrDefault("sets", "?") + " × " + p.getOrDefault("repetitions", "?") + " · " +
			(number(load, 0) > 0 ? weight(number(load, 0)) : Txt.t(R.string.ses_bodyweight_lower)) +
			" · " + Txt.t(R.string.ses_rest_seconds, p.getOrDefault("restSeconds", "60"));
	}

	/** «2 × 8 · 20 kg» si todas las series son iguales; si no, «20×8, 22,5×6…». */
	private String compactSets(List<SetLog.Entry> sets) {
		boolean same = true;
		for (SetLog.Entry x : sets)
			same &= x.equals(sets.get(0));
		if (same)
			return sets.size() + " × " + sets.get(0).reps + " · " + weight(sets.get(0).kg).toLowerCase(Locale.ROOT);
		List<String> parts = new ArrayList<>();
		for (SetLog.Entry x : sets)
			parts.add((x.kg > 0 ? SetLog.format(Plates.toUnit(x.kg, lb)) : Txt.t(R.string.ses_bodyweight_short)) + "×" + x.reps);
		return String.join(", ", parts);
	}

	private String weight(double kg) {
		return Plates.text(kg, lb);
	}

	private static double number(String value, double fallback) {
		try {
			return Double.parseDouble(value.trim().replace(',', '.'));
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static String day(Session s) {
		return java.time.Instant.ofEpochMilli(s.getCompletedAt()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
			.format(java.time.format.DateTimeFormatter.ofPattern("d/M"));
	}

	private static String dayLong(Session s) {
		String d = java.time.Instant.ofEpochMilli(s.getCompletedAt()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
			.format(java.time.format.DateTimeFormatter.ofPattern(Txt.t(R.string.pattern_weekday_day_month), Txt.locale()));
		return Character.toUpperCase(d.charAt(0)) + d.substring(1);
	}

	/** Sesión terminada más reciente en la que se registró este ejercicio (en cualquier rutina). */
	private Session lastTime(String exerciseId) {
		Session best = null;
		for (Session s : app.history)
			if (s.getStatus() == Session.Status.FINISHED && s.getResults().containsKey(exerciseId) &&
				(best == null || s.getCompletedAt() > best.getCompletedAt()))
				best = s;
		return best;
	}

	/** Temporizador de descanso entre series: cuenta atrás visible y vibración al terminar. */
	private void rest() {
		add(rows, new android.view.View(requireContext()), 8);
		LinearLayout card = card(Txt.t(R.string.ses_rest), "");
		restLabel = text(card, "", R.style.EnerGym_CardTitle, 0);
		LinearLayout presets = columns(card, 8);
		for (int seconds : new int[]{60, 90, 120})
			compact(presets, seconds / 60 + ":" + (seconds % 60 == 0 ? "00" : "30"), true, () -> {
				restEndsAt(SystemClock.elapsedRealtime() + seconds * 1000L,
					app.sessions.item() == null ? "" : app.sessions.item().getExercise().getName());
				tick();
			});
		compact(presets, "✕", true, () -> {
			restEndsAt(0, "");
			tick();
		}).setContentDescription(Txt.t(R.string.ses_stop_rest));
		tick();
	}

	private void tick() {
		ticker.removeCallbacks(tick);
		if (restLabel == null)
			return;
		long left = restEndsAt - SystemClock.elapsedRealtime();
		if (restEndsAt == 0) {
			restLabel.setText(Txt.t(R.string.ses_rest_hint));
			return;
		}
		if (left <= 0) {
			restEndsAt = 0;
			restLabel.setText(Txt.t(R.string.ses_rest_done));
			vibrate();
			return;
		}
		long secs = (left + 999) / 1000;
		restLabel.setText(String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60));
		ticker.postDelayed(tick, Math.min(1000, left));
	}

	private void vibrate() {
		try {
			Vibrator v = Build.VERSION.SDK_INT >= 31 ?
				((VibratorManager) requireContext().getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE))
					.getDefaultVibrator() :
				(Vibrator) requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
			if (v != null && v.hasVibrator())
				v.vibrate(VibrationEffect.createWaveform(new long[]{0, 250, 150, 250}, -1));
		} catch (RuntimeException ignored) {
			// Sin vibración el aviso en pantalla basta.
		}
	}

	private static List<String> choiceLabels(List<String> values) {
		List<String> labels = new ArrayList<>();
		for (String v : values)
			labels.add(MetricValidator.choiceLabel(v));
		return labels;
	}
}
