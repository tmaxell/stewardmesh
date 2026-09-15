package io.stewardmesh.masterdata.application.goldenrecord;

import io.stewardmesh.masterdata.application.port.in.GetSourceMasterLink;
import io.stewardmesh.masterdata.application.port.out.LoadSourceMasterLinks;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.util.List;
import java.util.Objects;

public final class SourceMasterLinkReadService implements GetSourceMasterLink {

    private final LoadSourceMasterLinks links;

    public SourceMasterLinkReadService(LoadSourceMasterLinks links) {
        this.links = Objects.requireNonNull(links, "links must not be null");
    }

    @Override
    public SourceMasterLink execute(SourceRecordIdentity source) {
        Objects.requireNonNull(source, "source must not be null");
        List<SourceMasterLink> active = links.findActive(source);
        if (active.isEmpty()) {
            throw new SourceMasterLinkReadException(false);
        }
        if (active.size() != 1) {
            throw new SourceMasterLinkReadException(true);
        }
        return active.getFirst();
    }
}
