package ai.greycos.solver.core.impl.bavet.visual;

import ai.greycos.solver.core.impl.bavet.common.AbstractNode;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraint;

record GraphSink<Solution_>(AbstractNode node, BavetConstraint<Solution_> constraint) {}
