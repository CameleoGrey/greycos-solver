package greycos.solver.core.impl.exhaustivesearch.decider;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.function.BiFunction;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.exhaustivesearch.node.ExhaustiveSearchLayer;
import greycos.solver.core.impl.exhaustivesearch.node.ExhaustiveSearchNode;
import greycos.solver.core.impl.exhaustivesearch.node.bounder.ScoreBounder;
import greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchPhaseScope;
import greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchStepScope;
import greycos.solver.core.impl.heuristic.move.SelectorBasedCompositeMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionSorter;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListAssignMove;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;
import greycos.solver.core.preview.api.move.builtin.Moves;

/**
 * Searches the product of all variable decisions. A list element is considered once, in a stable
 * order, and inserted at every legal position. Removing elements in reverse decision order gives
 * each final allocation a unique path, including the decision to leave an optional element out.
 */
public final class JointVariableExhaustiveSearchDecider<Solution_, Score_ extends Score<Score_>>
    extends AbstractExhaustiveSearchDecider<Solution_, Score_> {

  public record VariableSelector<Solution_>(
      GenuineVariableDescriptor<Solution_> descriptor,
      EntitySelector<Solution_> entities,
      MoveSelector<Solution_> moves,
      SelectionSorter<Solution_, Object> valueSorter) {}

  private record Coordinate<Solution_>(VariableSelector<Solution_> selector, Object subject) {}

  private static final class JointNode<Solution_> extends ExhaustiveSearchNode<Solution_> {
    private final BitSet decisions;

    private JointNode(
        ExhaustiveSearchLayer layer, ExhaustiveSearchNode<Solution_> parent, BitSet decisions) {
      super(layer, parent);
      this.decisions = decisions;
    }
  }

  public static final class DecisionFilter<Solution_> {
    private BiFunction<GenuineVariableDescriptor<Solution_>, Move<Solution_>, Move<Solution_>>
        selector;

    public Move<Solution_> select(
        GenuineVariableDescriptor<Solution_> descriptor, Move<Solution_> move) {
      return selector == null ? move : selector.apply(descriptor, move);
    }
  }

  private record SkipListValue<Solution_>(
      ListVariableDescriptor<Solution_> descriptor, Object value) implements Move<Solution_> {
    @Override
    public void execute(MutableSolutionView<Solution_> view) {
      // An optional element stays unassigned, but this decision is complete in the search tree.
    }

    @Override
    public Move<Solution_> rebase(Lookup lookup) {
      return new SkipListValue<>(descriptor, lookup.lookUpWorkingObject(value));
    }

    @Override
    public SequencedCollection<Object> getPlanningEntities() {
      return List.of();
    }

    @Override
    public SequencedCollection<Object> getPlanningValues() {
      return List.of(value);
    }
  }

  private final List<VariableSelector<Solution_>> selectors;
  private final List<MoveSelector<Solution_>> moveSelectors;
  private final PhaseTermination<Solution_> termination;
  private final DecisionFilter<Solution_> decisionFilter;
  private BitSet currentDecisions;
  private final List<Coordinate<Solution_>> coordinates = new ArrayList<>();
  private final Map<GenuineVariableDescriptor<Solution_>, Map<Object, Integer>> coordinateIndexes =
      new IdentityHashMap<>();

  public JointVariableExhaustiveSearchDecider(
      String logIndentation,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      PhaseTermination<Solution_> termination,
      List<VariableSelector<Solution_>> selectors,
      DecisionFilter<Solution_> decisionFilter,
      boolean scoreBounderEnabled,
      ScoreBounder<?> scoreBounder) {
    super(
        logIndentation,
        bestSolutionRecaller,
        termination,
        null,
        null,
        null,
        scoreBounderEnabled,
        scoreBounder);
    this.termination = termination;
    this.selectors = List.copyOf(selectors);
    this.decisionFilter = decisionFilter;
    this.decisionFilter.selector = this::selectLeaf;
    Set<MoveSelector<Solution_>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    moveSelectors = selectors.stream().map(VariableSelector::moves).filter(seen::add).toList();
    requireCompleteSolutionsForPessimisticBound();
  }

  @Override
  public void expandNode(ExhaustiveSearchStepScope<Solution_> stepScope) {
    ExhaustiveSearchNode<Solution_> node = stepScope.getExpandingNode();
    var decided = ((JointNode<Solution_>) node).decisions;
    currentDecisions = decided;
    // Build STEP caches only after restoring this node and selecting its decision frontier.
    moveSelectors.forEach(moveSelector -> moveSelector.stepStarted(stepScope));
    int nextIndex = decided.nextClearBit(0);
    var coordinate = coordinates.get(nextIndex);
    var selector = coordinate.selector();
    long evaluated = 0;
    for (var move : selector.moves()) {
      if (termination.isPhaseTerminated(stepScope.getPhaseScope())
          || Thread.currentThread().isInterrupted()) {
        break;
      }
      var updated = (BitSet) decided.clone();
      if (!recordDecisions(move, updated) || !updated.get(nextIndex)) {
        continue;
      }
      evaluate(stepScope, node, updated, move);
      evaluated++;
    }
    stepScope.setSelectedMoveCount(evaluated);
  }

  private Move<Solution_> selectLeaf(
      GenuineVariableDescriptor<Solution_> descriptor, Move<Solution_> move) {
    if (currentDecisions == null) {
      return null;
    }
    if (move instanceof SelectorBasedNoChangeMove<Solution_>) {
      if (descriptor instanceof ListVariableDescriptor<Solution_> list
          && list.allowsUnassignedValues()) {
        for (int index = currentDecisions.nextClearBit(0);
            index < coordinates.size();
            index = currentDecisions.nextClearBit(index + 1)) {
          var coordinate = coordinates.get(index);
          if (coordinate.selector().descriptor() == descriptor) {
            return new SkipListValue<>(list, coordinate.subject());
          }
        }
      }
      return null;
    }
    return recordDecisions(move, (BitSet) currentDecisions.clone()) ? move : null;
  }

  private boolean recordDecisions(Move<Solution_> move, BitSet decided) {
    if (move instanceof SelectorBasedCompositeMove<Solution_> composite) {
      for (var child : composite.getMoves()) {
        if (!recordDecisions(child, decided)) {
          return false;
        }
      }
      return true;
    }
    GenuineVariableDescriptor<Solution_> descriptor;
    Object subject;
    if (move instanceof SkipListValue<Solution_> skip) {
      descriptor = skip.descriptor();
      subject = skip.value();
    } else if (move instanceof ListAssignMove<Solution_> assign) {
      descriptor = assign.getVariableDescriptor();
      subject = assign.getMovedValue();
    } else if (move instanceof ChangeMove<Solution_> change) {
      descriptor = change.getVariableDescriptor();
      subject = change.getEntity();
    } else {
      return false;
    }
    var indexes = coordinateIndexes.get(descriptor);
    var index = indexes == null ? null : indexes.get(subject);
    if (index == null || decided.get(index)) {
      return false;
    }
    if (descriptor.isListVariable()) {
      // Composite candidates may reach a later descriptor, but each list retains its canonical
      // element order. This avoids revisiting permutations of the same insertion decisions.
      for (int earlier = decided.nextClearBit(0);
          earlier < index;
          earlier = decided.nextClearBit(earlier + 1)) {
        if (coordinates.get(earlier).selector().descriptor() == descriptor) {
          return false;
        }
      }
    }
    decided.set(index);
    return true;
  }

  private void evaluate(
      ExhaustiveSearchStepScope<Solution_> stepScope,
      ExhaustiveSearchNode<Solution_> parent,
      BitSet decided,
      Move<Solution_> move) {
    var nextLayer = stepScope.getPhaseScope().getLayerList().get(decided.cardinality());
    var child = new JointNode<Solution_>(nextLayer, parent, decided);
    child.setMove(move);
    doMove(stepScope, child, isSolutionComplete(child), false);
    stepScope.getPhaseScope().addMoveEvaluationCount(move, 1);
    stepScope.getPhaseScope().getSolverScope().checkYielding();
  }

  @Override
  public boolean isSolutionComplete(ExhaustiveSearchNode<Solution_> node) {
    return node.getDepth() == coordinates.size();
  }

  @Override
  protected boolean canPublishCompleteSolution(InnerScore<Score_> score) {
    // A user filter may leave a required assignment with no available candidate. It must not
    // turn a structurally incomplete state into an incumbent merely by exhausting coordinates.
    return score.isFullyAssigned();
  }

  @Override
  public boolean isEntityReinitializable(Object entity) {
    return true;
  }

  @Override
  public void restoreWorkingSolution(
      ExhaustiveSearchStepScope<Solution_> stepScope,
      boolean assertWorkingSolutionScoreFromScratch,
      boolean assertExpectedWorkingSolutionScore) {
    var phaseScope = stepScope.getPhaseScope();
    ExhaustiveSearchNode<Solution_> oldNode =
        phaseScope.getLastCompletedStepScope().getExpandingNode();
    ExhaustiveSearchNode<Solution_> newNode = stepScope.getExpandingNode();
    var undo = new ArrayList<Move<Solution_>>();
    var forward = new ArrayList<Move<Solution_>>();
    while (oldNode != newNode) {
      if (oldNode.getDepth() < newNode.getDepth()) {
        forward.add(newNode.getMove());
        newNode = newNode.getParent();
      } else {
        undo.add(oldNode.getUndoMove());
        oldNode = oldNode.getParent();
      }
    }
    if (undo.isEmpty() && forward.isEmpty()) {
      return;
    }
    undo.addAll(forward.reversed());
    phaseScope
        .getScoreDirector()
        .getMoveDirector()
        .executeAllowingStructurallyFlawedSolutions(Moves.compose(undo));
    var score = phaseScope.<Score_>calculateScore();
    var expected = stepScope.<Score_>getStartingStepScore();
    if (assertExpectedWorkingSolutionScore && expected != null) {
      phaseScope.assertExpectedWorkingScore(expected, undo);
    }
    if (assertWorkingSolutionScoreFromScratch) {
      phaseScope.assertPredictedScoreFromScratch(score, undo);
    }
    stepScope.getExpandingNode().setScore(score);
    phaseScope.getSolutionDescriptor().setScore(phaseScope.getWorkingSolution(), score.raw());
  }

  @Override
  protected void fillLayerList(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    releaseSearchState();
    var director = phaseScope.getScoreDirector();
    var solution = phaseScope.getWorkingSolution();
    for (var selector : selectors) {
      var descriptor = selector.descriptor();
      if (descriptor instanceof ListVariableDescriptor<Solution_> list) {
        var values = new ArrayList<Object>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var rangeDescriptor = descriptor.getValueRangeDescriptor();
        if (descriptor.canExtractValueRangeFromSolution()) {
          director
              .getValueRangeManager()
              .getFromSolution(rangeDescriptor)
              .createOriginalIterator()
              .forEachRemaining(
                  value -> {
                    if (seen.add(value)) values.add(value);
                  });
        } else {
          // Include pinned owners in the population; pinning restricts destinations, not values.
          for (var owner : descriptor.getEntityDescriptor().extractEntities(solution)) {
            director
                .getValueRangeManager()
                .getFromEntity(rangeDescriptor, owner)
                .createOriginalIterator()
                .forEachRemaining(
                    value -> {
                      if (seen.add(value)) values.add(value);
                    });
          }
        }
        if (selector.valueSorter() != null) {
          selector.valueSorter().sort(solution, values);
        }
        var state = director.getListVariableState(list);
        for (var value : values) {
          if (!state.isAssigned(value)) {
            coordinates.add(new Coordinate<>(selector, value));
          }
        }
      } else {
        for (var entity : selector.entities()) {
          if (descriptor.isReinitializable(entity)) {
            coordinates.add(new Coordinate<>(selector, entity));
          }
        }
      }
    }
    var layers = new ArrayList<ExhaustiveSearchLayer>(coordinates.size() + 1);
    for (int i = 0; i < coordinates.size(); i++) {
      layers.add(new ExhaustiveSearchLayer(i, coordinates.get(i).subject()));
      var coordinate = coordinates.get(i);
      coordinateIndexes
          .computeIfAbsent(coordinate.selector().descriptor(), ignored -> new IdentityHashMap<>())
          .put(coordinate.subject(), i);
    }
    layers.add(new ExhaustiveSearchLayer(coordinates.size(), null));
    phaseScope.setLayerList(layers);
  }

  @Override
  protected void initStartNode(
      ExhaustiveSearchPhaseScope<Solution_> phaseScope, ExhaustiveSearchLayer ignored) {
    var layer = phaseScope.getLayerList().getFirst();
    var start = new JointNode<Solution_>(layer, null, new BitSet(coordinates.size()));
    if (scoreBounderEnabled) {
      var director = phaseScope.<Score_>getScoreDirector();
      var score = director.calculateScore();
      start.setScore(score);
      var incumbent = phaseScope.getSolverScope().getBestScore();
      phaseScope.setBestPessimisticBound(
          incumbent != null && incumbent.isFullyAssigned() && !incumbent.isStructurallyFlawed()
              ? incumbent
              : null);
      if (!score.isStructurallyFlawed()) {
        start.setOptimisticBound(
            isSolutionComplete(start)
                ? score
                : getScoreBounder().calculateOptimisticBound(director, score));
      }
    }
    if (!layer.isLastLayer()) {
      phaseScope.addExpandableNode(start);
    }
    phaseScope.getLastCompletedStepScope().setExpandingNode(start);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    selectors.forEach(selector -> selector.entities().solvingStarted(solverScope));
    moveSelectors.forEach(selector -> selector.solvingStarted(solverScope));
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    try {
      selectors.forEach(selector -> selector.entities().solvingEnded(solverScope));
      moveSelectors.forEach(selector -> selector.solvingEnded(solverScope));
    } finally {
      releaseSearchState();
    }
  }

  @Override
  public void phaseStarted(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    selectors.forEach(selector -> selector.entities().phaseStarted(phaseScope));
    moveSelectors.forEach(selector -> selector.phaseStarted(phaseScope));
    var initialStep = new ExhaustiveSearchStepScope<>(phaseScope);
    selectors.forEach(selector -> selector.entities().stepStarted(initialStep));
    try {
      fillLayerList(phaseScope);
    } finally {
      selectors.forEach(selector -> selector.entities().stepEnded(initialStep));
    }
    initStartNode(phaseScope, null);
  }

  @Override
  public void phaseEnded(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    selectors.forEach(selector -> selector.entities().phaseEnded(phaseScope));
    moveSelectors.forEach(selector -> selector.phaseEnded(phaseScope));
  }

  @Override
  public void stepStarted(ExhaustiveSearchStepScope<Solution_> stepScope) {
    // Selection starts in expandNode(), after this node's solution has been restored.
  }

  @Override
  public void stepEnded(ExhaustiveSearchStepScope<Solution_> stepScope) {
    moveSelectors.forEach(selector -> selector.stepEnded(stepScope));
  }

  @Override
  public void releaseSearchState() {
    coordinates.clear();
    coordinateIndexes.clear();
    currentDecisions = null;
  }
}
