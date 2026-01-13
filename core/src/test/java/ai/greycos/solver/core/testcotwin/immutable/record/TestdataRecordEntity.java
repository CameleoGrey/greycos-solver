package ai.greycos.solver.core.testcotwin.immutable.record;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public record TestdataRecordEntity(@PlanningVariable String code) {}
