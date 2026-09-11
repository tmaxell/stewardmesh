package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordQuery;
import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordView;

public interface GetGoldenRecord extends UseCase<GoldenRecordQuery, GoldenRecordView> {}
