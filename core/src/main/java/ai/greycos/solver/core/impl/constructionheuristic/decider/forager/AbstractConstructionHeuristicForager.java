package ai.greycos.solver.core.impl.constructionheuristic.decider.forager;

import ai.greycos.solver.core.impl.constructionheuristic.event.ConstructionHeuristicPhaseLifecycleListenerAdapter;

public abstract class AbstractConstructionHeuristicForager<Solution_>
    extends ConstructionHeuristicPhaseLifecycleListenerAdapter<Solution_>
    implements ConstructionHeuristicForager<Solution_> {}
