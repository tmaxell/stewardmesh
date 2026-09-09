package io.stewardmesh.masterdata.application.intake;

import java.io.IOException;
import java.io.InputStream;

/** Re-openable workbook content supplied to application and storage boundaries. */
public interface IntakeContent {

    String contentType();

    long sizeBytes();

    InputStream openStream() throws IOException;
}
