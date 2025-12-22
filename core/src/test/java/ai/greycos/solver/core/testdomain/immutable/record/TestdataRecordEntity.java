package ai.greycos.solver.core.testdomain.immutable.record;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public record TestdataRecordEntity(@PlanningVariable String code) {}
