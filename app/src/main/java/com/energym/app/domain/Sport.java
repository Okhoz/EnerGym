package com.energym.app.domain;

import com.energym.app.R;
import com.energym.app.i18n.Txt;

import java.util.Objects;

public final class Sport {
	public enum CoverageStatus {EXAMPLE_UNREVIEWED, PENDING_VALIDATION}

	private final String id, name, nameEn;
	private final CoverageStatus specificCoverage, complementaryCoverage;

	public Sport(String id, String name, CoverageStatus specific, CoverageStatus complementary) {
		this(id, name, null, specific, complementary);
	}

	/** {@code nameEn}: nombre en inglés del catálogo; null o vacío si no lo tiene. */
	public Sport(String id, String name, String nameEn, CoverageStatus specific, CoverageStatus complementary) {
		this.id = TrainingProfile.required(id, "sportId");
		this.name = TrainingProfile.required(name, "sportName");
		this.nameEn = nameEn == null || nameEn.trim().isEmpty() ? null : nameEn.trim();
		specificCoverage = Objects.requireNonNull(specific, "specific");
		complementaryCoverage = Objects.requireNonNull(complementary, "complementary");
	}

	public String getId() {
		return id;
	}

	/** Nombre en el idioma de la app. */
	public String getName() {
		return nameEn != null && Txt.english() ? nameEn : name;
	}

	/** Nombre original del catálogo (español): el que recibe la IA. */
	public String getSourceName() {
		return name;
	}

	public CoverageStatus getSpecificCoverage() {
		return specificCoverage;
	}

	public CoverageStatus getComplementaryCoverage() {
		return complementaryCoverage;
	}

	public String getCoverageMessage(TrainingProfile.Focus focus) {
		CoverageStatus status = focus == TrainingProfile.Focus.SPECIFIC ? specificCoverage : complementaryCoverage;
		return Txt.t(status == CoverageStatus.PENDING_VALIDATION ? R.string.sport_coverage_pending :
			R.string.sport_coverage_example);
	}
}
