package com.energym.app.domain;
import java.util.*;
public final class Routine {
    private final String proposalId, title, explanation;
    private final long versionId;
    private final TrainingProfile profile;
    private final List<RoutineItem> items;

    public Routine(String proposalId, long versionId, String title, TrainingProfile profile,
            List<RoutineItem> items, String explanation) {
        this.proposalId = TrainingProfile.required(proposalId, "proposalId");
        if (versionId < 0) throw new IllegalArgumentException("Versión negativa");
        this.versionId = versionId;
        this.title = TrainingProfile.required(title, "title");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.items = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(items, "items")));
        for (RoutineItem item : this.items) Objects.requireNonNull(item, "item");
        this.explanation = TrainingProfile.required(explanation, "explanation");
    }
    public String getProposalId() { return proposalId; }
    public long getVersionId() { return versionId; }
    public String getTitle() { return title; }
    public TrainingProfile getProfile() { return profile; }
    public List<RoutineItem> getItems() { return items; }
    public String getExplanation() { return explanation; }
    public boolean isEmpty() { return items.isEmpty(); }
    public int getEstimatedMinutes() {
        int total = 0;
        for (RoutineItem item : items) total += item.getEstimatedMinutes();
        return total;
    }
    public Routine withVersionId(long id) { return new Routine(proposalId, id, title, profile, items, explanation); }
    /** Revalidate immediately before accepting; any profile change makes the old proposal stale. */
    public boolean isValidFor(TrainingProfile current) {
        if (!profile.equals(current) || items.isEmpty() || getEstimatedMinutes() > current.getDurationMinutes()) return false;
        CompatibilityEngine engine = new CompatibilityEngine();
        Set<String> ids = new HashSet<>();
        for (RoutineItem item : items) {
            if (!engine.isCompatible(item.getExercise(), current) || !ids.add(item.getExercise().getId())) return false;
        }
        return true;
    }
}
