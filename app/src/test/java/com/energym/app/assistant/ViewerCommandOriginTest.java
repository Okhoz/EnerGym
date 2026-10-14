package com.energym.app.assistant;

import com.energym.app.domain.Exercise;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * El origen se captura al enviar; una respuesta no adopta la pantalla posterior.
 */
public final class ViewerCommandOriginTest {
	@Test
	public void correlatedOpenCarriesOriginalRevisionAndLatestRequestGuard() {
		ProposalControllerTest.Client client = new ProposalControllerTest.Client();
		Exercise exercise = ProposalControllerTest.exercise("squat", "Técnica");
		ProposalController controller =
			new ProposalController(new ProposalControllerTest.Store(), new ProposalControllerTest.Memory(), client,
				Collections.singletonList(exercise), () -> null, () -> {
			}, routine -> {
			});
		long[] navigation = {7};
		List<ProposalController.OpenExercise> commands = new ArrayList<>();
		controller.activate("demo");
		controller.explain(exercise);
		controller.openCommands(() -> navigation[0], commands::add);
		controller.sendExternal("Abre la referencia");
		navigation[0] = 9;
		client.callback.success(ProposalControllerTest.validReply(client.request)
			.replace("\"commands\":[]", "\"commands\":[{\"type\":\"OPEN_EXERCISE\",\"exerciseId\":\"squat\"}]"));
		assertEquals(1, commands.size());
		assertEquals(7, commands.get(0).originRevision);
		assertEquals(client.request.requestId, commands.get(0).requestId);
		controller.sendExternal("Abre otra vez");
		ChatClient.Callback late = client.callback;
		controller.explain(exercise);
		late.success(ProposalControllerTest.validReply(client.request)
			.replace("\"commands\":[]", "\"commands\":[{\"type\":\"OPEN_EXERCISE\",\"exerciseId\":\"squat\"}]"));
		assertEquals(1, commands.size());
	}
}
