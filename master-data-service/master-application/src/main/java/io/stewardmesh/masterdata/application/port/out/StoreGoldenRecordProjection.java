package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordProjection;

public interface StoreGoldenRecordProjection {

    void save(GoldenRecordProjection projection);
}
