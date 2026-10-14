package com.energym.app.assistant;

import com.energym.app.domain.*;

import java.util.*;

/**
 * MODEL: conversación local aislada por propietario; nunca forma parte de la sincronización cloud.
 */
public interface ConversationStore {
    final class Message {
        public final String role, text, source;

        public Message(String role, String text) {
            this(role, text, "local");
        }

        public Message(String role, String text, String source) {
            this.role = role;
            this.text = text;
            this.source = source;
        }
    }

    final class State {
        public final String conversationId;
        public final List<Message> messages;
        public final Map<String, String> fields;
        public final Routine preview;
        public final TrainingProfile base, previewBase;
        public final Exercise exercise;

        public State(String id, List<Message> messages, Map<String, String> fields, Routine preview, TrainingProfile base, Exercise exercise) {
            this(id, messages, fields, preview, base, exercise, base);
        }

        /**
         * previewBase es el CAS inmutable de la propuesta, independiente del perfil mutable del diálogo.
         */
        public State(String id, List<Message> messages, Map<String, String> fields, Routine preview, TrainingProfile base, Exercise exercise, TrainingProfile previewBase) {
            conversationId = id;
            this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
            this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            this.preview = preview;
            this.base = base;
            this.exercise = exercise;
            this.previewBase = previewBase;
        }
    }

    State load(String owner);

    void save(String owner, State state);
}

