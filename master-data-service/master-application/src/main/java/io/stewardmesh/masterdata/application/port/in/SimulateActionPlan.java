package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.actionplan.SimulateActionPlanCommand;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanSimulation;

/** SIMULATE boundary: explains what a sealed plan would do, and mutates nothing. */
@FunctionalInterface
public interface SimulateActionPlan
        extends UseCase<SimulateActionPlanCommand, ActionPlanSimulation> {}
