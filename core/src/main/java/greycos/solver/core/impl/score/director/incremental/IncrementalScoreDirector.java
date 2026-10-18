package greycos.solver.core.impl.score.director.incremental;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.score.FloatingScoreAccumulator;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.calculator.AnalyzableIncrementalScoreCalculator;
import greycos.solver.core.api.score.calculator.ConstraintMatchRegistration;
import greycos.solver.core.api.score.calculator.ConstraintMatchRegistry;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.analysis.AnalysisDetails;
import greycos.solver.core.impl.score.analysis.DefaultMatchOccurrenceAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultRawScoreAnalysis;
import greycos.solver.core.impl.score.analysis.IndictmentAnalysisBuilder;
import greycos.solver.core.impl.score.analysis.IndictmentMaps;
import greycos.solver.core.impl.score.constraint.ConstraintMatch;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.constraint.ConstraintMatchTotal;
import greycos.solver.core.impl.score.director.AbstractScoreDirector;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.util.MutableReference;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Incremental java implementation of {@link ScoreDirector}, which only recalculates the {@link
 * Score} of the part of the {@link PlanningSolution working solution} that changed, instead of the
 * going through the entire {@link PlanningSolution}. This is incremental calculation, which is
 * fast.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @param <Score_> the score type to go with the solution
 * @see ScoreDirector
 */
@NullMarked
public final class IncrementalScoreDirector<Solution_, Score_ extends Score<Score_>>
    extends AbstractScoreDirector<
        Solution_, Score_, IncrementalScoreDirectorFactory<Solution_, Score_>>
    implements ConstraintMatchRegistry<Score_> {

  private final IncrementalScoreCalculator<Solution_, Score_> incrementalScoreCalculator;
  private final boolean constraintMatchEnabled;
  private Score_ totalScore;
  private final @Nullable FloatingScoreAccumulator<Score_> floatingAccumulator;
  private final SortedMap<ConstraintRef, ConstraintMatchTotal<Score_>> constraintMatchTotalMap =
      new TreeMap<>();
  private final @Nullable Map<ConstraintMatch<Score_>, SelectedObjects> selectedObjectsByMatch;
  private long registrationEpoch;

  private IncrementalScoreDirector(Builder<Solution_, Score_> builder) {
    super(builder);
    this.incrementalScoreCalculator =
        Objects.requireNonNull(
            builder.incrementalScoreCalculator, "The incrementalScoreCalculator must not be null.");
    this.constraintMatchEnabled = getConstraintMatchPolicy().isEnabled();
    this.selectedObjectsByMatch =
        indictmentFetchPolicy() == IndictmentFetchPolicy.NONE ? null : new IdentityHashMap<>();
    this.totalScore = getScoreDefinition().getZeroScore();
    this.floatingAccumulator =
        FloatingScoreSupport.isFloatingScore(totalScore)
            ? FloatingScoreAccumulator.create(totalScore)
            : null;
    if (incrementalScoreCalculator
            instanceof
            AnalyzableIncrementalScoreCalculator<Solution_, Score_>
                analyzableIncrementalScoreCalculator
        && constraintMatchEnabled) {
      analyzableIncrementalScoreCalculator.enableConstraintMatch(this);
    }
  }

  public IncrementalScoreCalculator<Solution_, Score_> getIncrementalScoreCalculator() {
    return incrementalScoreCalculator;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

  @Override
  public void setWorkingSolutionWithoutUpdatingShadows(Solution_ workingSolution) {
    super.setWorkingSolutionWithoutUpdatingShadows(workingSolution, null);
  }

  @Override
  protected void beforeShadowVariablesReset(Solution_ workingSolution) {
    registrationEpoch = Math.incrementExact(registrationEpoch);
    constraintMatchTotalMap.clear();
    if (selectedObjectsByMatch != null) {
      selectedObjectsByMatch.clear();
    }
    totalScore = getScoreDefinition().getZeroScore();
    if (floatingAccumulator != null) {
      floatingAccumulator.clear();
    }
    incrementalScoreCalculator.resetWorkingSolution(workingSolution);
  }

  @Override
  public InnerScore<Score_> innerCalculateScore() {
    variableSupport.assertShadowVariablesAreUpToDate();
    var score =
        Objects.requireNonNull(
            incrementalScoreCalculator.calculateScore(),
            () ->
                "The incrementalScoreCalculator (%s) must return a non-null score in the method calculateScore()."
                    .formatted(incrementalScoreCalculator));
    setCalculatedScore(score);
    return new InnerScore<>(score, -getWorkingInitScore());
  }

  @Override
  public Map<ConstraintRef, ConstraintMatchTotal<Score_>> getConstraintMatchTotalMap() {
    ensureWorkingSolutionStateFresh();
    if (!constraintMatchPolicy.isEnabled()) {
      throw new IllegalStateException(
          "When constraint matching ("
              + constraintMatchPolicy
              + ") is disabled in the constructor, this method should not be called.");
    }
    // Notice that we don't update the shadow variables
    return constraintMatchTotalMap;
  }

  @Override
  public IndictmentFetchPolicy indictmentFetchPolicy() {
    return getConstraintMatchPolicy().getIndictmentFetchPolicy();
  }

  @Override
  public IndictmentMaps<Score_> getIndictmentMaps() {
    return extractAnalysisDetails(false).indictments();
  }

  @Override
  public AnalysisDetails<Score_> getAnalysisDetails() {
    return extractAnalysisDetails(constraintMatchPolicy.isMatchOccurrenceEnabled());
  }

  private AnalysisDetails<Score_> extractAnalysisDetails(boolean includeMatchOccurrences) {
    var policy = indictmentFetchPolicy();
    if (policy == IndictmentFetchPolicy.NONE) {
      return AnalysisDetails.unavailable();
    }
    var terminal = new IndictmentAnalysisBuilder<>(getScoreDefinition().getZeroScore());
    var original =
        policy == IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL
            ? new IndictmentAnalysisBuilder<>(getScoreDefinition().getZeroScore())
            : null;
    List<MatchOccurrenceAnalysis<Score_>> occurrences =
        includeMatchOccurrences ? new ArrayList<>() : null;
    for (var total : getConstraintMatchTotalMap().values()) {
      for (var match : total.getConstraintMatchSet()) {
        var selected = Objects.requireNonNull(selectedObjectsByMatch).get(match);
        if (selected == null || selected.terminal() == null) {
          throw missingIndictments(match, "terminal");
        }
        if (occurrences != null) {
          var rawSelected = (RawSelectedObjects) selected;
          if (original != null && rawSelected.originalSources == null) {
            throw missingIndictments(match, "original");
          }
          occurrences.add(
              new DefaultMatchOccurrenceAnalysis<>(
                  match.getConstraintRef(),
                  match.getScore(),
                  Objects.requireNonNull(match.getJustification()),
                  1,
                  Objects.requireNonNull(rawSelected.terminalOccurrences),
                  rawSelected.originalSources));
        }
        terminal.add(match, selected.terminal());
        if (original != null) {
          if (selected.original() == null) {
            throw missingIndictments(match, "original");
          }
          original.add(match, selected.original());
        }
      }
    }
    return new AnalysisDetails<>(
        new IndictmentMaps<>(terminal.build(), original == null ? null : original.build()),
        occurrences == null ? null : new DefaultRawScoreAnalysis<>(occurrences, original != null));
  }

  private IllegalStateException missingIndictments(ConstraintMatch<Score_> match, String view) {
    return new IllegalStateException(
        "The incremental score calculator (%s) registered a match for constraint (%s) without %s indictment metadata. Use registerConstraintMatchWithIndictments() to supply the requested objects."
            .formatted(
                incrementalScoreCalculator.getClass().getSimpleName(),
                match.getConstraintRef(),
                view));
  }

  @Override
  public boolean requiresFlushing() {
    return true; // Incremental may decide to keep events for delayed processing.
  }

  // ************************************************************************
  // Entity/variable add/change/remove methods
  // ************************************************************************

  @Override
  public void afterEntityAdded(EntityDescriptor<Solution_> entityDescriptor, Object entity) {
    super.afterEntityAdded(entityDescriptor, entity);
  }

  @Override
  public void beforeVariableChanged(
      VariableDescriptor<Solution_> variableDescriptor, Object entity) {
    ensureWorkingSolutionStateFresh();
    incrementalScoreCalculator.beforeVariableChanged(entity, variableDescriptor.getVariableName());
    super.beforeVariableChanged(variableDescriptor, entity);
  }

  @Override
  public void afterVariableChanged(
      VariableDescriptor<Solution_> variableDescriptor, Object entity) {
    incrementalScoreCalculator.afterVariableChanged(entity, variableDescriptor.getVariableName());
    super.afterVariableChanged(variableDescriptor, entity);
  }

  @Override
  public void beforeListVariableElementAssigned(
      ListVariableDescriptor<Solution_> variableDescriptor, Object element) {
    ensureWorkingSolutionStateFresh();
    incrementalScoreCalculator.beforeListVariableElementAssigned(
        variableDescriptor.getVariableName(), element);
    super.beforeListVariableElementAssigned(variableDescriptor, element);
  }

  @Override
  public void afterListVariableElementAssigned(
      ListVariableDescriptor<Solution_> variableDescriptor, Object element) {
    incrementalScoreCalculator.afterListVariableElementAssigned(
        variableDescriptor.getVariableName(), element);
    super.afterListVariableElementAssigned(variableDescriptor, element);
  }

  @Override
  public void beforeListVariableElementUnassigned(
      ListVariableDescriptor<Solution_> variableDescriptor, Object element) {
    ensureWorkingSolutionStateFresh();
    incrementalScoreCalculator.beforeListVariableElementUnassigned(
        variableDescriptor.getVariableName(), element);
    super.beforeListVariableElementUnassigned(variableDescriptor, element);
  }

  @Override
  public void afterListVariableElementUnassigned(
      ListVariableDescriptor<Solution_> variableDescriptor, Object element) {
    incrementalScoreCalculator.afterListVariableElementUnassigned(
        variableDescriptor.getVariableName(), element);
    super.afterListVariableElementUnassigned(variableDescriptor, element);
  }

  @Override
  public void beforeListVariableChanged(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object entity,
      int fromIndex,
      int toIndex) {
    ensureWorkingSolutionStateFresh();
    incrementalScoreCalculator.beforeListVariableChanged(
        entity, variableDescriptor.getVariableName(), fromIndex, toIndex);
    super.beforeListVariableChanged(variableDescriptor, entity, fromIndex, toIndex);
  }

  @Override
  public void afterListVariableChanged(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object entity,
      int fromIndex,
      int toIndex) {
    incrementalScoreCalculator.afterListVariableChanged(
        entity, variableDescriptor.getVariableName(), fromIndex, toIndex);
    super.afterListVariableChanged(variableDescriptor, entity, fromIndex, toIndex);
  }

  @Override
  public void afterEntityRemoved(EntityDescriptor<Solution_> entityDescriptor, Object entity) {
    super.afterEntityRemoved(entityDescriptor, entity);
  }

  // ************************************************************************
  // Problem fact add/change/remove methods
  // ************************************************************************

  @Override
  public void beforeProblemFactAdded(Object problemFact) {
    super.beforeProblemFactAdded(problemFact);
  }

  @Override
  public void afterProblemFactAdded(Object problemFact) {
    super.afterProblemFactAdded(problemFact);
  }

  @Override
  public void afterProblemPropertyChanged(Object problemFactOrEntity) {
    super.afterProblemPropertyChanged(problemFactOrEntity);
  }

  @Override
  public void afterProblemFactRemoved(Object problemFact) {
    super.afterProblemFactRemoved(problemFact);
  }

  @Override
  public ConstraintMatchRegistration<Score_> registerConstraintMatch(
      ConstraintRef constraintRef, Score_ score, ConstraintJustification justification) {
    Collection<?> terminalObjects = null;
    if (selectedObjectsByMatch != null
        && justification instanceof DefaultConstraintJustification defaultJustification) {
      terminalObjects = defaultJustification.getFacts();
    }
    return register(
        constraintRef, score, justification, captureSelectedObjects(terminalObjects, true, null));
  }

  @Override
  public ConstraintMatchRegistration<Score_> registerConstraintMatchWithIndictments(
      ConstraintRef constraintRef,
      Score_ score,
      ConstraintJustification justification,
      Collection<?> indictedObjects) {
    Objects.requireNonNull(indictedObjects, "indictedObjects");
    return register(
        constraintRef, score, justification, captureSelectedObjects(indictedObjects, false, null));
  }

  @Override
  public ConstraintMatchRegistration<Score_> registerConstraintMatchWithIndictments(
      ConstraintRef constraintRef,
      Score_ score,
      ConstraintJustification justification,
      Collection<?> indictedObjects,
      Collection<?> originalIndictedObjects) {
    Objects.requireNonNull(indictedObjects, "indictedObjects");
    Objects.requireNonNull(originalIndictedObjects, "originalIndictedObjects");
    return register(
        constraintRef,
        score,
        justification,
        captureSelectedObjects(indictedObjects, false, originalIndictedObjects));
  }

  private @Nullable SelectedObjects captureSelectedObjects(
      @Nullable Collection<?> terminal, boolean omitNulls, @Nullable Collection<?> original) {
    if (selectedObjectsByMatch == null) {
      return null;
    }
    boolean includeOriginal =
        indictmentFetchPolicy() == IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL;
    if (!constraintMatchPolicy.isMatchOccurrenceEnabled()) {
      return new SelectedObjects(
          terminal == null ? null : copySelectedObjects(terminal, omitNulls),
          includeOriginal && original != null ? copySelectedObjects(original, false) : null);
    }
    // Raw terminal multiplicity and source identity are retained only for an explicit raw request.
    var rawTerminal = terminal == null ? null : copyTerminalOccurrences(terminal, omitNulls);
    var rawOriginal = includeOriginal && original != null ? copyOriginalSources(original) : null;
    return new RawSelectedObjects(
        rawTerminal == null ? null : copySelectedObjects(rawTerminal, false),
        rawOriginal == null ? null : copySelectedObjects(rawOriginal, false),
        rawTerminal,
        rawOriginal);
  }

  private static List<Object> copyTerminalOccurrences(Collection<?> objects, boolean omitNulls) {
    var copy = new ArrayList<Object>();
    for (var object : objects) {
      if (object != null || !omitNulls) {
        copy.add(Objects.requireNonNull(object, "An indicted object must not be null."));
      }
    }
    return List.copyOf(copy);
  }

  private static List<Object> copyOriginalSources(Collection<?> objects) {
    var distinct = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    var copy = new ArrayList<Object>();
    for (var object : objects) {
      Objects.requireNonNull(object, "An original indicted object must not be null.");
      if (distinct.add(object)) copy.add(object);
    }
    return List.copyOf(copy);
  }

  private static List<Object> copySelectedObjects(Collection<?> objects, boolean omitNulls) {
    var distinct = new LinkedHashSet<Object>();
    for (var object : objects) {
      if (object != null || !omitNulls) {
        distinct.add(Objects.requireNonNull(object, "An indicted object must not be null."));
      }
    }
    return List.copyOf(distinct);
  }

  private ConstraintMatchRegistration<Score_> register(
      ConstraintRef constraintRef,
      Score_ score,
      ConstraintJustification justification,
      @Nullable SelectedObjects selected) {
    if (!constraintMatchEnabled) {
      throw new IllegalStateException(
          "Cannot register constraint match (%s) when constraint matching (%s) is disabled in the constructor."
              .formatted(constraintRef, constraintMatchPolicy));
    }
    Objects.requireNonNull(constraintRef, "constraintRef");
    Objects.requireNonNull(score, "score");
    Objects.requireNonNull(justification, "justification");
    var total = constraintMatchTotalMap.get(constraintRef);
    if (total == null) {
      total = new ConstraintMatchTotal<>(constraintRef, score.zero());
      constraintMatchTotalMap.put(constraintRef, total);
    }

    var match = total.addConstraintMatch(justification, score);
    if (selected != null) {
      selectedObjectsByMatch.put(match, selected);
    }
    if (floatingAccumulator == null) {
      totalScore = totalScore.add(score);
    } else {
      floatingAccumulator.add(score);
    }
    var effectiveTotal = total;
    var canceled = new MutableReference<>(false);
    var epoch = registrationEpoch;
    return new DefaultConstraintMatchRegistration<>(
        match,
        selected,
        () -> {
          if (epoch != registrationEpoch) {
            throw new IllegalStateException(
                "Constraint match (%s) was already canceled by resetting the working solution."
                    .formatted(match));
          }
          if (Objects.requireNonNullElse(canceled.getValue(), false)) {
            throw new IllegalStateException(
                "Constraint match (%s) can only be canceled once.".formatted(match));
          }
          canceled.setValue(true);
          if (floatingAccumulator == null) {
            totalScore = totalScore.subtract(score);
          } else {
            floatingAccumulator.subtract(score);
          }
          effectiveTotal.removeConstraintMatch(match);
          if (selectedObjectsByMatch != null) {
            selectedObjectsByMatch.remove(match);
          }
        });
  }

  @Override
  public Score_ totalScore() {
    return floatingAccumulator == null ? totalScore : floatingAccumulator.extractScore();
  }

  private record DefaultConstraintMatchRegistration<Score_ extends Score<Score_>>(
      ConstraintMatch<Score_> constraintMatch,
      @Nullable SelectedObjects selectedObjects,
      Runnable undo)
      implements ConstraintMatchRegistration<Score_> {

    @Override
    public ConstraintRef constraintRef() {
      return constraintMatch.getConstraintRef();
    }

    @Override
    public Score_ score() {
      return constraintMatch.getScore();
    }

    @Override
    public ConstraintJustification justification() {
      return Objects.requireNonNull(constraintMatch.getJustification());
    }

    @Override
    public @Nullable List<Object> indictedObjects() {
      return selectedObjects == null ? null : selectedObjects.terminal();
    }

    @Override
    public @Nullable List<Object> originalIndictedObjects() {
      return selectedObjects == null ? null : selectedObjects.original();
    }

    @Override
    public void cancel() {
      undo.run();
    }
  }

  private static class SelectedObjects {
    private final @Nullable List<Object> terminal;
    private final @Nullable List<Object> original;

    private SelectedObjects(@Nullable List<Object> terminal, @Nullable List<Object> original) {
      this.terminal = terminal;
      this.original = original;
    }

    private @Nullable List<Object> terminal() {
      return terminal;
    }

    private @Nullable List<Object> original() {
      return original;
    }
  }

  private static final class RawSelectedObjects extends SelectedObjects {
    private final @Nullable List<Object> terminalOccurrences;
    private final @Nullable List<Object> originalSources;

    private RawSelectedObjects(
        @Nullable List<Object> terminal,
        @Nullable List<Object> original,
        @Nullable List<Object> terminalOccurrences,
        @Nullable List<Object> originalSources) {
      super(terminal, original);
      this.terminalOccurrences = terminalOccurrences;
      this.originalSources = originalSources;
    }
  }

  @NullMarked
  public static final class Builder<Solution_, Score_ extends Score<Score_>>
      extends AbstractScoreDirectorBuilder<
          Solution_,
          Score_,
          IncrementalScoreDirectorFactory<Solution_, Score_>,
          Builder<Solution_, Score_>> {

    private @Nullable IncrementalScoreCalculator<Solution_, Score_> incrementalScoreCalculator;

    public Builder(IncrementalScoreDirectorFactory<Solution_, Score_> scoreDirectorFactory) {
      this(scoreDirectorFactory, scoreDirectorFactory.getGlobalEnvironmentMode());
    }

    public Builder(
        IncrementalScoreDirectorFactory<Solution_, Score_> scoreDirectorFactory,
        EnvironmentMode environmentMode) {
      super(scoreDirectorFactory, environmentMode);
    }

    public Builder<Solution_, Score_> withIncrementalScoreCalculator(
        IncrementalScoreCalculator<Solution_, Score_> incrementalScoreCalculator) {
      this.incrementalScoreCalculator = incrementalScoreCalculator;
      return withConstraintMatchPolicy(
          constraintMatchPolicy); // Ensure the policy is correct for the calculator.
    }

    @Override
    public Builder<Solution_, Score_> withConstraintMatchPolicy(
        ConstraintMatchPolicy constraintMatchPolicy) {
      return super.withConstraintMatchPolicy(
          determineCorrectPolicy(constraintMatchPolicy, incrementalScoreCalculator));
    }

    @Override
    public IncrementalScoreDirector<Solution_, Score_> build() {
      return new IncrementalScoreDirector<>(this);
    }

    private static ConstraintMatchPolicy determineCorrectPolicy(
        ConstraintMatchPolicy constraintMatchPolicy,
        @Nullable IncrementalScoreCalculator<?, ?> incrementalScoreCalculator) {
      if (incrementalScoreCalculator == null) {
        return ConstraintMatchPolicy.DISABLED;
      }
      return incrementalScoreCalculator instanceof AnalyzableIncrementalScoreCalculator<?, ?>
          ? constraintMatchPolicy
          : ConstraintMatchPolicy.DISABLED;
    }
  }
}
