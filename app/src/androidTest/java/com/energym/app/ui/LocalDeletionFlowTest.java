package com.energym.app.ui;

import android.app.Dialog;
import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.controller.AppController;
import com.energym.app.data.*;
import com.energym.app.domain.*;
import com.energym.app.assistant.LocalConversationStore;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Solo fixtures demo del emulador: confirma que cancelar conserva y borrar cierra el contexto.
 */
public class LocalDeletionFlowTest {
	@Test
	public void deleteRequiresConfirmationAndLeavesOtherOwnerIntact() throws Exception {
		Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		String other = "delete-other-" + java.util.UUID.randomUUID();
		try (EnerGymDatabase demo = new EnerGymDatabase(context, EnerGymDatabase.DEMO_OWNER_UID);
			 EnerGymDatabase foreign = new EnerGymDatabase(context, other)) {
			demo.clearLocalData();
			demo.saveProfile(new TrainingProfile.Builder().preferences("fixture propio").build());
			foreign.saveProfile(new TrainingProfile.Builder().preferences("fixture ajeno").build());
			try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
				MainFlowTest.await();
				scenario.onActivity(a -> a.state().accounts.enterDemo());
				MainFlowTest.await();
				scenario.onActivity(a -> {
					// La rutina solo se prepara si se pide expresamente (entrenador conversacional).
					a.state().assistant.sendLocal("Prepárame una rutina de 3 días en casa sin material 30min fuerza principiante");
					assertNotNull(a.state().assistant.preview);
					a.state().app.navigate(AppController.Screen.ACCOUNT);
				});
				MainFlowTest.setting(scenario, "device");
				MainFlowTest.click(scenario, "Borrar datos locales");
				scenario.onActivity(a -> {
					androidx.fragment.app.DialogFragment fragment =
						(androidx.fragment.app.DialogFragment) a.getSupportFragmentManager()
							.findFragmentByTag("delete-local-confirmation");
					assertNotNull(fragment);
					((androidx.appcompat.app.AlertDialog) fragment.requireDialog()).getButton(Dialog.BUTTON_NEGATIVE)
						.performClick();
				});
				assertNotNull(demo.loadProfile());
				MainFlowTest.setting(scenario, "device");
				MainFlowTest.click(scenario, "Borrar datos locales");
				scenario.onActivity(a -> {
					androidx.fragment.app.DialogFragment fragment =
						(androidx.fragment.app.DialogFragment) a.getSupportFragmentManager()
							.findFragmentByTag("delete-local-confirmation");
					((androidx.appcompat.app.AlertDialog) fragment.requireDialog()).getButton(Dialog.BUTTON_POSITIVE)
						.performClick();
				});
				MainFlowTest.await();
				scenario.onActivity(a -> {
					assertNull(a.state().app.owner());
					assertNull(a.state().app.profile);
					assertNull(a.state().app.routines.preview);
					assertNull(a.state().app.routines.accepted);
					assertNull(a.state().assistant.preview);
					assertTrue(a.state().assistant.messages.isEmpty());
				});
				assertNull(demo.loadProfile());
				assertTrue(new RoutineRepository(demo).versions().isEmpty());
				assertTrue(new SessionRepository(demo).pending().isEmpty());
				assertNull(new LocalConversationStore(context, Runnable::run).load(EnerGymDatabase.DEMO_OWNER_UID));
				assertEquals("fixture ajeno", foreign.loadProfile().getPreferences());
			} finally {
				foreign.clearLocalData();
				demo.clearLocalData();
			}
		}
	}
}
