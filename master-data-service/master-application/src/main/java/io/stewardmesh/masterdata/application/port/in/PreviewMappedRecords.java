package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.intake.MappedRecordsPreview;
import io.stewardmesh.masterdata.application.intake.PreviewMappedRecordsCommand;

/** Produces a bounded aggregate preview for an explicit mapping selection. */
@FunctionalInterface
public interface PreviewMappedRecords extends UseCase<PreviewMappedRecordsCommand, MappedRecordsPreview> {}
