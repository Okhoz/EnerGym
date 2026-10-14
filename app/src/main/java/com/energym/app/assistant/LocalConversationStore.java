package com.energym.app.assistant;

import android.content.Context;
import android.util.AtomicFile;

import com.energym.app.data.SnapshotCodec;
import com.energym.app.domain.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * ADAPTADOR local: snapshot acotado, archivo atómico por UID hash dentro de noBackupFilesDir; cero tokens.
 */
public final class LocalConversationStore implements ConversationStore {
	private final File folder;
	private final Executor worker;
	private final Map<String, State> cache = new HashMap<>();

	public LocalConversationStore(Context context, Executor worker) {
		folder = new File(context.getNoBackupFilesDir(), "conversations");
		this.worker = worker;
	}

	private AtomicFile file(String owner) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(owner.getBytes(StandardCharsets.UTF_8));
			StringBuilder name = new StringBuilder();
			for (byte b : hash)
				name.append(String.format(Locale.ROOT, "%02x", b & 255));
			return new AtomicFile(new File(folder, name + ".json"));
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo abrir conversación local");
		}
	}

	// Las previews del formato anterior carecen de CAS propio: se descartan sin borrar mensajes ni datos aceptados.
	public synchronized State load(String owner) {
		if (cache.containsKey(owner))
			return cache.get(owner);
		State state = null;
		try {
			AtomicFile f = file(owner);
			if (f.getBaseFile().length() > 262144)
				throw new IOException();
			Map<String, Object> root =
				StrictJson.object(StrictJson.parse(new String(f.readFully(), StandardCharsets.UTF_8), 262144));
			List<Message> messages = new ArrayList<>();
			for (Object row : StrictJson.array(root.get("messages"), 48)) {
				Map<String, Object> m = StrictJson.object(row);
				String role = StrictJson.text(m.get("role"), 9);
				if (!StrictJson.set("user", "assistant").contains(role))
					throw new IOException();
				String source = m.containsKey("source") ? StrictJson.text(m.get("source"), 6) : "local";
				if (!StrictJson.set("local", "remote").contains(source))
					throw new IOException();
				messages.add(new Message(role, StrictJson.text(m.get("text"), 4000), source));
			}
			Map<String, String> fields = new LinkedHashMap<>();
			for (Map.Entry<String, Object> e : StrictJson.object(root.get("fields")).entrySet())
				fields.put(e.getKey(), (String) e.getValue());
			state = new State(StrictJson.text(root.get("conversationId"), 80), messages, fields,
				!root.containsKey("previewBase") || root.get("preview") == null ? null :
					SnapshotCodec.decodeRoutine((String) root.get("preview")),
				root.get("base") == null ? null : SnapshotCodec.decodeProfile((String) root.get("base")),
				root.get("exercise") == null ? null : SnapshotCodec.decodeExercise((String) root.get("exercise")),
				root.get("previewBase") == null ? null : SnapshotCodec.decodeProfile((String) root.get("previewBase")));
		} catch (
			Exception ignored) {/* Archivo ausente/corrupto: inicia conversación nueva sin filtrar datos de otra cuenta. */}
		cache.put(owner, state);
		return state;
	}

	public synchronized void save(String owner, State state) {
		cache.put(owner, state);
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("conversationId", state.conversationId);
		List<Object> m = new ArrayList<>();
		for (Message x : state.messages)
			m.add(StrictJson.map("role", x.role, "text", x.text, "source", x.source));
		root.put("messages", m);
		root.put("fields", state.fields);
		root.put("preview", state.preview == null ? null : SnapshotCodec.encodeRoutine(state.preview));
		root.put("base", state.base == null ? null : SnapshotCodec.encodeProfile(state.base));
		root.put("previewBase", state.previewBase == null ? null : SnapshotCodec.encodeProfile(state.previewBase));
		root.put("exercise", state.exercise == null ? null : SnapshotCodec.encodeExercise(state.exercise));
		byte[] bytes = StrictJson.write(root).getBytes(StandardCharsets.UTF_8);
		worker.execute(() -> {
			AtomicFile f = file(owner);
			FileOutputStream out = null;
			try {
				if (!folder.isDirectory() && !folder.mkdirs())
					throw new IOException();
				out = f.startWrite();
				out.write(bytes);
				f.finishWrite(out);
			} catch (IOException e) {
				if (out != null)
					f.failWrite(out);
			}
		});
	}
}



