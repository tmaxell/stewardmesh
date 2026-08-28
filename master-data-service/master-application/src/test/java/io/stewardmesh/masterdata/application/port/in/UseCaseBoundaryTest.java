package io.stewardmesh.masterdata.application.port.in;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UseCaseBoundaryTest {

    @Test
    void coordinatesDomainValuesWithoutAFramework() {
        var partyId = new SupplierPartyId(
                UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10a"));
        UseCase<SupplierPartyId, SupplierPartyId> identityUseCase = command -> command;

        assertEquals(partyId, identityUseCase.execute(partyId));
    }
}
