package io.stewardmesh.agent.eval;

import java.io.InputStream;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

/** Reads a frozen dataset without coupling the reference agent to a model provider. */
public final class FrozenEvalDatasetLoader {

    private final ObjectMapper json;

    public FrozenEvalDatasetLoader() {
        this(new ObjectMapper());
    }

    FrozenEvalDatasetLoader(ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "json must not be null");
    }

    public FrozenEvalDataset load(InputStream input) {
        return json.readValue(
                Objects.requireNonNull(input, "input must not be null"), FrozenEvalDataset.class);
    }
}
