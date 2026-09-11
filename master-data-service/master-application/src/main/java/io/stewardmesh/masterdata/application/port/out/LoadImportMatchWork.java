package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.identity.ImportMatchWorkItem;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import java.util.List;

@FunctionalInterface
public interface LoadImportMatchWork {

    List<ImportMatchWorkItem> load(ImportJobId importJobId);
}
