package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.application.intake.IntakeContent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.springframework.web.multipart.MultipartFile;

final class MultipartIntakeContent implements IntakeContent {

    private final MultipartFile file;
    private final String contentType;

    MultipartIntakeContent(MultipartFile file, String contentType) {
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public long sizeBytes() {
        return file.getSize();
    }

    @Override
    public InputStream openStream() throws IOException {
        return file.getInputStream();
    }
}
