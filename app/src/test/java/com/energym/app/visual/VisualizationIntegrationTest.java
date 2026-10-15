package com.energym.app.visual;

import com.energym.app.catalog.CatalogRepository;
import com.energym.app.controller.AppController;
import com.energym.app.domain.*;

import org.junit.Test;

import java.nio.file.*;
import java.util.*;

import static org.junit.Assert.*;

/**
 * La autorización pertenece al contexto seleccionado, incluso antes de existir una ventana hija.
 */
public final class VisualizationIntegrationTest {
	private ExerciseResourcePolicy policy() throws Exception {
		Exercise e = new Exercise("standard-pushup", "Flexión estándar", MetricType.STRENGTH,
			EnumSet.of(TrainingProfile.Goal.STRENGTH), EnumSet.of(TrainingProfile.Environment.HOME),
			Collections.emptySet(), Collections.singleton("strength"), Exercise.Coverage.SPECIFIC, false, "Técnica",
			Collections.emptyList());
		CatalogRepository catalog = new CatalogRepository(2, Collections.singletonList(
			new Sport("strength", "Fuerza", Sport.CoverageStatus.EXAMPLE_UNREVIEWED,
				Sport.CoverageStatus.EXAMPLE_UNREVIEWED)), Collections.singletonList(e));
		return new ExerciseResourcePolicy(catalog, Collections.singletonList(PackagedManifest.pushUp()));
	}

	@Test
	public void exactSnapshotAndVariantAvailability() throws Exception {
		ExerciseResourcePolicy p = policy();
		Exercise current = p.available().get(0);
		assertEquals("standard-pushup", current.getId());
		assertTrue(p.available(current));
		Exercise historical =
			new Exercise(current.getId(), current.getName(), current.getMetricType(), current.getGoals(),
				current.getEnvironments(), current.getRequiredResources(), current.getSportIds(), current.getCoverage(),
				true, current.getDescription(), current.getAlternativeIds());
		assertFalse(p.available(historical));
		assertFalse(p.available(null));
	}

	@Test
	public void pendingLaunchRevokedBeforeChildExists() throws Exception {
		ExerciseResourcePolicy p = policy();
		Exercise e = p.available().get(0);
		List<VisualizationController.Request> opened = new ArrayList<>();
		List<String> revoked = new ArrayList<>();
		VisualizationController v = new VisualizationController(p, new VisualizationController.Leases() {
			public void publish(VisualizationController.Request r) {
				ViewerLeaseRegistry.publish(r.lease, r.exerciseId);
			}

			public void revoke(String lease) {
				revoked.add(lease);
				ViewerLeaseRegistry.revoke(lease);
			}
		}, () -> {
		});
		v.bind(opened::add);
		v.context("demo", 1, AppController.Screen.EXERCISE, e);
		assertTrue(v.open(e));
		VisualizationController.Request r = opened.get(0);
		assertTrue(ViewerLeaseRegistry.valid(r.lease, r.exerciseId));
		v.context(null, 2, AppController.Screen.HOME, null);
		assertFalse(ViewerLeaseRegistry.valid(r.lease, r.exerciseId));
		assertEquals(Collections.singletonList(r.lease), revoked);
		assertFalse(v.open(e));
		v.result(r.lease);
	}

	@Test
	public void lateAssistantNavigationAndDuplicateRequestsRejected() throws Exception {
		ExerciseResourcePolicy p = policy();
		Exercise e = p.available().get(0);
		List<VisualizationController.Request> opened = new ArrayList<>();
		VisualizationController v = new VisualizationController(p, new VisualizationController.Leases() {
			public void publish(VisualizationController.Request r) {
			}

			public void revoke(String lease) {
			}
		}, () -> {
		});
		v.bind(opened::add);
		v.context("demo", 4, AppController.Screen.ASSISTANT, e);
		assertFalse(v.assistant(e, e, "c", "old", 3));
		assertTrue(v.assistant(e, e, "c", "r", 4));
		assertFalse(v.assistant(e, e, "c", "r", 4));
		v.result(opened.get(0).lease);
		assertFalse(v.assistant(e, e, "c", "r", 4));
		v.context("demo", 6, AppController.Screen.ASSISTANT, e);
		assertFalse(v.assistant(e, e, "c", "late", 4));
		assertEquals(1, opened.size());
	}
}
