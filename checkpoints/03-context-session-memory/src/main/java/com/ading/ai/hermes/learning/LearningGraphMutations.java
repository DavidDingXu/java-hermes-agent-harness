package com.ading.ai.hermes.learning;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LearningGraphMutations {

    private LearningGraphMutations() {
    }

    public static LearningGraphDocument upsertMemory(
            LearningGraphDocument document,
            LearningMemory memory
    ) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(memory, "memory must not be null");
        return validated(new LearningGraphDocument(
                upsert(document.memories(), memory, LearningMemory::id),
                document.skills()
        ));
    }

    public static LearningGraphDocument deleteMemory(
            LearningGraphDocument document,
            String memoryId
    ) {
        Objects.requireNonNull(document, "document must not be null");
        return validated(new LearningGraphDocument(
                delete(document.memories(), memoryId, LearningMemory::id),
                document.skills()
        ));
    }

    public static LearningGraphDocument upsertSkill(
            LearningGraphDocument document,
            LearningSkill skill
    ) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(skill, "skill must not be null");
        return validated(new LearningGraphDocument(
                document.memories(),
                upsert(document.skills(), skill, LearningSkill::id)
        ));
    }

    public static LearningGraphDocument deleteSkill(
            LearningGraphDocument document,
            String skillId
    ) {
        Objects.requireNonNull(document, "document must not be null");
        return validated(new LearningGraphDocument(
                document.memories(),
                delete(document.skills(), skillId, LearningSkill::id)
        ));
    }

    private static LearningGraphDocument validated(LearningGraphDocument candidate) {
        LearningGraphSnapshot snapshot = candidate.snapshot();
        if (!snapshot.diagnostics().isEmpty()) {
            LearningGraphDiagnostic diagnostic = snapshot.diagnostics().getFirst();
            throw new IllegalArgumentException(
                    "learning graph mutation rejected: " + diagnostic.message()
            );
        }
        return candidate;
    }

    private static <T> List<T> upsert(
            List<T> values,
            T replacement,
            java.util.function.Function<T, String> id
    ) {
        List<T> updated = new ArrayList<>(values);
        for (int index = 0; index < updated.size(); index++) {
            if (id.apply(updated.get(index)).equals(id.apply(replacement))) {
                updated.set(index, replacement);
                return List.copyOf(updated);
            }
        }
        updated.add(replacement);
        return List.copyOf(updated);
    }

    private static <T> List<T> delete(
            List<T> values,
            String valueId,
            java.util.function.Function<T, String> id
    ) {
        Objects.requireNonNull(values, "values must not be null");
        if (valueId == null || valueId.isBlank()) {
            throw new IllegalArgumentException("learning node id must not be blank");
        }
        return values.stream().filter(value -> !id.apply(value).equals(valueId.trim())).toList();
    }
}
