package com.energym.app.data;

import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;
import java.util.*;
import org.json.*;

/** Frontera JSON Android: conserva el perfil completo y ejercicios originales sin consultar el catálogo. */
public final class SnapshotCodec {
    private SnapshotCodec() { }
    /** Serializa el snapshot exacto para el contexto conversacional local, sin buscar el ID en el catálogo. */
    public static String encodeExercise(Exercise e){try{return exercise(e).toString();}catch(JSONException error){throw invalid(error);}}
    public static Exercise decodeExercise(String json){try{return exercise(new JSONObject(json));}catch(JSONException error){throw invalid(error);}}
    public static String encodeProfile(TrainingProfile p) {
        try { return profile(p).toString(); } catch (JSONException e) { throw invalid(e); }
    }
    public static TrainingProfile decodeProfile(String json) {
        try { return profile(new JSONObject(json)); } catch (JSONException e) { throw invalid(e); }
    }
    public static String encodeRoutine(Routine r) {
        try { return routine(r).toString(); } catch (JSONException e) { throw invalid(e); }
    }
    public static Routine decodeRoutine(String json) {
        try { return routine(new JSONObject(json)); } catch (JSONException e) { throw invalid(e); }
    }
    public static String encodeSession(Session s) {
        try {
            JSONObject results = new JSONObject();
            for (Map.Entry<String,Map<String,String>> entry : s.getResults().entrySet()) results.put(entry.getKey(), strings(entry.getValue()));
            return new JSONObject().put("syncId", s.getSyncId()).put("routine", routine(s.getRoutine()))
                    .put("results", results).put("startedAt", s.getStartedAt()).put("completedAt", s.getCompletedAt())
                    .put("status", s.getStatus().name()).toString();
        } catch (JSONException e) { throw invalid(e); }
    }
    public static Session decodeSession(String json) {
        try {
            JSONObject o = new JSONObject(json); Map<String,Map<String,String>> results = new LinkedHashMap<>();
            JSONObject values = o.getJSONObject("results"); Iterator<String> ids = values.keys();
            while (ids.hasNext()) { String id = ids.next(); results.put(id, strings(values.getJSONObject(id))); }
            return new Session(o.getString("syncId"), routine(o.getJSONObject("routine")), results,
                    o.getLong("startedAt"), o.getLong("completedAt"), Session.Status.valueOf(o.getString("status")));
        } catch (JSONException e) { throw invalid(e); }
    }
    private static JSONObject profile(TrainingProfile p) throws JSONException {
        return new JSONObject().put("sportId", p.getSportId()).put("goal", p.getGoal().name())
                .put("environment", p.getEnvironment().name()).put("focus", p.getFocus().name()).put("category", p.getCategory().name())
                .put("resources", array(p.getResources())).put("daysPerWeek", p.getDaysPerWeek()).put("durationMinutes", p.getDurationMinutes())
                .put("level", p.getLevel()).put("experience", p.getExperience()).put("space", p.getSpace())
                .put("preferences", p.getPreferences()).put("limitations", p.getLimitations()).put("historyConsent", p.isHistoryConsent());
    }
    private static TrainingProfile profile(JSONObject o) throws JSONException {
        return new TrainingProfile.Builder().sportId(o.getString("sportId")).goal(Goal.valueOf(o.getString("goal")))
                .environment(Environment.valueOf(o.getString("environment"))).focus(Focus.valueOf(o.getString("focus")))
                .category(Category.valueOf(o.getString("category"))).resources(enums(o.getJSONArray("resources"), Resource.class))
                .daysPerWeek(o.getInt("daysPerWeek")).durationMinutes(o.getInt("durationMinutes")).level(o.getString("level"))
                .experience(o.getString("experience")).space(o.getString("space")).preferences(o.getString("preferences"))
                .limitations(o.getString("limitations")).historyConsent(o.getBoolean("historyConsent")).build();
    }
    private static JSONObject routine(Routine r) throws JSONException {
        JSONArray items = new JSONArray();
        for (RoutineItem item : r.getItems()) items.put(new JSONObject().put("exercise", exercise(item.getExercise()))
                .put("prescription", strings(item.getPrescription())).put("estimatedMinutes", item.getEstimatedMinutes())
                .put("justification", item.getJustification()));
        return new JSONObject().put("proposalId", r.getProposalId()).put("versionId", r.getVersionId()).put("title", r.getTitle())
                .put("profile", profile(r.getProfile())).put("items", items).put("explanation", r.getExplanation());
    }
    private static Routine routine(JSONObject o) throws JSONException {
        List<RoutineItem> items = new ArrayList<>(); JSONArray rows = o.getJSONArray("items");
        for (int i = 0; i < rows.length(); i++) { JSONObject row = rows.getJSONObject(i);
            items.add(new RoutineItem(exercise(row.getJSONObject("exercise")), strings(row.getJSONObject("prescription")),
                    row.getInt("estimatedMinutes"), row.getString("justification"))); }
        return new Routine(o.getString("proposalId"), o.getLong("versionId"), o.getString("title"), profile(o.getJSONObject("profile")),
                items, o.getString("explanation"));
    }
    private static JSONObject exercise(Exercise e) throws JSONException {
        return new JSONObject().put("id", e.getId()).put("name", e.getName()).put("metricType", e.getMetricType().name())
                .put("goals", array(e.getGoals())).put("environments", array(e.getEnvironments())).put("resources", array(e.getRequiredResources()))
                .put("sportIds", array(e.getSportIds())).put("coverage", e.getCoverage().name()).put("reviewed", e.isReviewed())
                .put("description", e.getDescription()).put("alternatives", array(e.getAlternativeIds()));
    }
    private static Exercise exercise(JSONObject o) throws JSONException {
        return new Exercise(o.getString("id"), o.getString("name"), MetricType.valueOf(o.getString("metricType")),
                enums(o.getJSONArray("goals"), Goal.class), enums(o.getJSONArray("environments"), Environment.class),
                enums(o.getJSONArray("resources"), Resource.class), new LinkedHashSet<>(list(o.getJSONArray("sportIds"))),
                Exercise.Coverage.valueOf(o.getString("coverage")), o.getBoolean("reviewed"), o.getString("description"), list(o.getJSONArray("alternatives")));
    }
    private static JSONArray array(Collection<?> values) { JSONArray a = new JSONArray(); for (Object v : values) a.put(v instanceof Enum<?> ? ((Enum<?>) v).name() : v); return a; }
    private static List<String> list(JSONArray a) throws JSONException { List<String> values = new ArrayList<>(); for (int i=0; i<a.length(); i++) values.add(a.getString(i)); return values; }
    private static <E extends Enum<E>> Set<E> enums(JSONArray a, Class<E> type) throws JSONException { Set<E> values = EnumSet.noneOf(type); for (String v : list(a)) values.add(Enum.valueOf(type, v)); return values; }
    private static JSONObject strings(Map<String,String> map) throws JSONException { JSONObject o = new JSONObject(); for (Map.Entry<String,String> e : map.entrySet()) o.put(e.getKey(), e.getValue()); return o; }
    private static Map<String,String> strings(JSONObject o) throws JSONException { Map<String,String> map = new LinkedHashMap<>(); Iterator<String> keys = o.keys(); while(keys.hasNext()) { String k=keys.next(); map.put(k,o.getString(k)); } return map; }
    private static IllegalArgumentException invalid(Exception e) { return new IllegalArgumentException("Snapshot inválido", e); }
}
