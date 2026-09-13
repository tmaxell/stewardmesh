package io.stewardmesh.masterdata.domain.actionplan;

/** Closed vocabulary of mutations supported by the onboarding plan. */
public enum ActionType {
    CREATE_SUPPLIER_PARTY(ActionRisk.MEDIUM),
    LINK_SOURCE_RECORD(ActionRisk.HIGH),
    CREATE_SUPPLIER_SITE(ActionRisk.MEDIUM),
    ASSIGN_SUPPLIER_SITE(ActionRisk.HIGH);

    private final ActionRisk risk;

    ActionType(ActionRisk risk) {
        this.risk = risk;
    }

    public ActionRisk risk() {
        return risk;
    }
}
