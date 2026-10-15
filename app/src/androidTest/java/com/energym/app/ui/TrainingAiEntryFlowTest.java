package com.energym.app.ui;

import android.view.View;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.assistant.ChatClient;
import com.energym.app.assistant.ConversationStore;
import com.energym.app.assistant.ProposalController;
import com.energym.app.controller.AppController;
import com.energym.app.controller.AppStore;
import com.energym.app.domain.Routine;
import com.energym.app.domain.Session;
import com.energym.app.domain.TrainingProfile;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Controlador real con conversación de memoria y transporte retenido; conserva la cuenta Google abierta. */
@RunWith(AndroidJUnit4.class)
public final class TrainingAiEntryFlowTest {
	@Test public void choosingAiReplacesLocalGuidanceWithoutSendingUntilExplicitSend() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			boolean[] ready = {false};
			for (int attempt = 0; attempt < 30 && !ready[0]; attempt++) {
				scenario.onActivity(activity -> ready[0] = activity.state().app != null &&
					!activity.state().app.loading && activity.state().app.initialDataReady && activity.state().assistant != null);
				if (!ready[0])
					MainFlowTest.await();
			}
			Assume.assumeTrue("La prueba utiliza una cuenta ya abierta, sin iniciar ni cerrar sesión.", ready[0]);
			AtomicReference<ProposalController> previous = new AtomicReference<>();
			AtomicReference<ProposalController> fixture = new AtomicReference<>();
			AppController.Screen[] previousScreen = {null};
			TrainingViewState.Pane[] previousPane = {null};
			String[] owner = {null};
			OnlineFixture client = new OnlineFixture();
			try {
				scenario.onActivity(activity -> {
					AppController app = activity.state().app;
					Assume.assumeTrue(app.owner() != null && !activity.state().assistant.busy && !app.trainingMutation);
					owner[0] = app.owner();
					previous.set(activity.state().assistant);
					previousScreen[0] = app.screen;
					previousPane[0] = activity.state().training.pane;
					ProposalController controller = new ProposalController(new ReadOnlyStore(), new MemoryConversation(),
						client, app.catalog.getExercises(), app.catalog.getVersion(), () -> app.profile, app::changed,
						routine -> { throw new AssertionError("La entrada a IA no acepta rutinas"); });
					fixture.set(controller);
					activity.state().assistant = controller;
					controller.activate(owner[0]);
					controller.selectLocalGuidance(true);
					activity.state().training.select(TrainingViewState.Pane.AI);
					app.navigate(AppController.Screen.TRAINING);
				});
				MainFlowTest.await();
				MainFlowTest.click(scenario, "Crear rutina con IA");
				MainFlowTest.await();
				scenario.onActivity(activity -> {
					ProposalController controller = fixture.get();
					assertSame(controller, activity.state().assistant);
					assertEquals(owner[0], activity.state().app.owner());
					assertFalse("Crear con IA debe abandonar el modo guiado local", controller.localGuidance());
					assertEquals(AppController.Screen.ASSISTANT, activity.state().app.screen);
					EditText input = activity.findViewById(R.id.chat_input);
					assertNotNull(input);
					assertEquals("Prepara una rutina para mí.", input.getText().toString());
					assertEquals("Preparar el mensaje no lo envía", 0, client.sends);
					assertFalse(controller.busy);
					activity.findViewById(R.id.chat_send).performClick();
					assertEquals(1, client.sends);
					assertEquals("Prepara una rutina para mí.", client.sent.message);
					assertTrue(controller.busy);
					activity.state().app.navigate(AppController.Screen.TRAINING);
				});
				MainFlowTest.await();
				scenario.onActivity(activity -> {
					View create = MainFlowTest.text(activity.getWindow().getDecorView(), "Crear rutina con IA");
					assertNotNull(create);
					assertFalse("No se prepara otra solicitud mientras la IA está ocupada", create.isEnabled());
					assertEquals(1, client.sends);
				});
			} finally {
				scenario.onActivity(activity -> {
					if (fixture.get() != null && activity.state().assistant == fixture.get()) {
						activity.state().assistant = previous.get();
						if (Objects.equals(owner[0], activity.state().app.owner())) {
							activity.state().training.select(previousPane[0]);
							activity.state().app.navigate(previousScreen[0]);
						} else {
							previous.get().activate(activity.state().app.owner());
						}
					}
				});
			}
		}
	}

	private static final class OnlineFixture implements ChatClient {
		int sends;
		Request sent;

		public void activate(String owner) {
		}

		public void send(Request request, Callback callback) {
			sends++;
			sent = request;
		}

		public void connect(String endpoint, String code, Callback callback) {
			throw new AssertionError("El modo online no se empareja");
		}

		public void disconnect() {
		}

		public boolean connected() {
			return true;
		}

		public boolean automaticConnection() {
			return true;
		}
	}

	private static final class MemoryConversation implements ConversationStore {
		private final Map<String, State> values = new HashMap<>();

		public State load(String owner) {
			return values.get(owner);
		}

		public void save(String owner, State value) {
			values.put(owner, value);
		}

		public void clear(String owner) {
			values.remove(owner);
		}
	}

	private static final class ReadOnlyStore implements AppStore {
		public void load(Callback<Snapshot> callback) {
			throw new AssertionError("Sin acceso al almacén real");
		}

		public void profile(TrainingProfile value, Callback<Void> callback) {
			throw new AssertionError("Sin escrituras");
		}

		public void accept(Routine value, Callback<Routine> callback) {
			throw new AssertionError("Sin escrituras");
		}

		public void draft(Session value, Callback<Long> callback) {
			throw new AssertionError("Sin escrituras");
		}

		public void openDraft(String syncId, Callback<OpenDraft> callback) {
			throw new AssertionError("Sin acceso al almacén real");
		}

		public void finish(long id, Callback<Void> callback) {
			throw new AssertionError("Sin escrituras");
		}
	}
}
