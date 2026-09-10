package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.stewardship.StewardshipCase;

@FunctionalInterface
public interface StoreStewardshipCase {

    void save(StewardshipCase stewardshipCase);
}
