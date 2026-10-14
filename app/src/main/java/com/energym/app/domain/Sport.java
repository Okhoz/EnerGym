package com.energym.app.domain;
import java.util.Objects;
public final class Sport {
    public enum CoverageStatus { EXAMPLE_UNREVIEWED, PENDING_VALIDATION }
    private final String id, name;
    private final CoverageStatus specificCoverage, complementaryCoverage;
    public Sport(String id, String name, CoverageStatus specific, CoverageStatus complementary) {
        this.id = TrainingProfile.required(id, "sportId");
        this.name = TrainingProfile.required(name, "sportName");
        specificCoverage = Objects.requireNonNull(specific, "specific");
        complementaryCoverage = Objects.requireNonNull(complementary, "complementary");
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public CoverageStatus getSpecificCoverage() { return specificCoverage; }
    public CoverageStatus getComplementaryCoverage() { return complementaryCoverage; }
    public String getCoverageMessage(TrainingProfile.Focus focus) {
        CoverageStatus status = focus == TrainingProfile.Focus.SPECIFIC ? specificCoverage : complementaryCoverage;
        return status == CoverageStatus.PENDING_VALIDATION ? "Contenido especializado pendiente de validación"
                : "Ejemplos de producto sin revisión profesional";
    }
}
