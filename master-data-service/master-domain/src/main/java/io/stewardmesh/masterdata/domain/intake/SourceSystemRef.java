package io.stewardmesh.masterdata.domain.intake;

/** Origin-system identity supplied by the caller, independent of transport and storage. */
public record SourceSystemRef(String value) {

    private static final int MAX_LENGTH = 128;

    public SourceSystemRef {
        value = DomainText.requireNonBlank(value, "source system", MAX_LENGTH);
    }
}
