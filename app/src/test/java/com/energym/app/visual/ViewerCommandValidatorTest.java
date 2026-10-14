package com.energym.app.visual;

import org.junit.Test;
import static org.junit.Assert.*;

/** Eliminar la correlación o las listas permitidas debe romper estas pruebas. */
public class ViewerCommandValidatorTest {
    private final ResourceManifest resource = ResourceManifest.pushUp();
    private final ViewerCommandValidator validator = new ViewerCommandValidator();
    @Test public void refusesAnotherExerciseAndUnknownTarget() {
        assertFalse(validator.validate(new ViewerCommand("knee-pushup", ViewerCommand.Action.PLAY, "", 0, 0), resource));
        assertFalse(validator.validate(new ViewerCommand("standard-pushup", ViewerCommand.Action.REGION, "unknown", 0, 0), resource));
        assertFalse(validator.validate(new ViewerCommand("standard-pushup", ViewerCommand.Action.VIEW, "unknown", 0, 0), resource));
        assertFalse(validator.validate(new ViewerCommand("standard-pushup", ViewerCommand.Action.MARKER, "absent", 0, 0), resource));
    }
    @Test public void allowsOnlyDeclaredRealControls() {
        assertTrue(validator.validate(new ViewerCommand("standard-pushup", ViewerCommand.Action.MARKER, "final", 0, 0), resource));
        assertTrue(validator.validate(new ViewerCommand("standard-pushup", ViewerCommand.Action.SPEED, "", .5f, 0), resource));
        assertFalse(validator.validate(new ViewerCommand("standard-pushup", ViewerCommand.Action.SPEED, "", .3f, 0), resource));
        assertFalse(validator.validate(new ViewerCommand("standard-pushup", ViewerCommand.Action.ZOOM, "", Float.NaN, 0), resource));
        assertFalse(validator.validate(new ViewerCommand("standard-pushup", ViewerCommand.Action.LAYER, "muscles", 0, 0), resource));
        assertFalse(validator.validate(null, resource));
        assertFalse(validator.validate(new ViewerCommand("standard-pushup", ViewerCommand.Action.PLAY, "", 0, 0), null));
    }
    @Test public void technicalPreviewDoesNotUnlockVerifiedDemonstration() {
        assertFalse(resource.professionallyReviewed);
        assertFalse(resource.capabilities.contains("verified-demonstration"));
        assertEquals(java.util.Collections.singleton("Human.rig"), resource.clipNames);
    }
}
