package com.energym.app.visual;

import org.junit.Test;
import static org.junit.Assert.*;

/** El controlador no permite comandos antes de cargar ni después de cerrar. */
public class ViewerControllerTest {
    @Test public void controlsRequireReadyAndCorrectExercise() {
        ViewerController controller = new ViewerController(ResourceManifest.pushUp(), state -> {});
        assertFalse(controller.command(new ViewerCommand("standard-pushup", ViewerCommand.Action.PAUSE, "", 0, 0)));
        controller.ready();
        assertTrue(controller.command(new ViewerCommand("standard-pushup", ViewerCommand.Action.PAUSE, "", 0, 0)));
        assertFalse(controller.state().playing);
        assertFalse(controller.command(new ViewerCommand("knee-pushup", ViewerCommand.Action.PLAY, "", 0, 0)));
        controller.close();
        assertFalse(controller.command(new ViewerCommand("standard-pushup", ViewerCommand.Action.PLAY, "", 0, 0)));
    }
    @Test public void finalMarkerStaysAtFinalWithoutWrappingToZero() {
        ViewerController controller = new ViewerController(ResourceManifest.pushUp(), state -> {});
        controller.ready();
        controller.command(new ViewerCommand("standard-pushup", ViewerCommand.Action.MARKER, "final", 0, 0));
        controller.advance(2);
        assertFalse(controller.state().playing);
        assertEquals(1.533333f, controller.state().playhead, .00001f);
    }
    @Test public void backgroundAndRestorationPreservePoseAndControls() {
        ViewerController controller = new ViewerController(ResourceManifest.pushUp(), state -> {});
        controller.ready();
        controller.active(true); controller.advance(.4f); controller.active(false); controller.advance(9);
        assertEquals(.4f, controller.state().playhead, .00001f);
        controller.command(new ViewerCommand("standard-pushup", ViewerCommand.Action.SPEED, "", .25f, 0));
        ViewerController restored = new ViewerController(ResourceManifest.pushUp(), state -> {});
        restored.restore(controller.state()); restored.ready();
        assertEquals(.4f, restored.state().playhead, .00001f);
        assertEquals(.25f, restored.state().speed, 0);
        assertFalse(restored.state().active);
    }
}
