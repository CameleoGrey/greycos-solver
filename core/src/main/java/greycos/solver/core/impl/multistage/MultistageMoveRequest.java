package greycos.solver.core.impl.multistage;

import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.CrossVariableStageProvider;
import greycos.solver.core.api.solver.multistage.ListVariableStageProvider;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.impl.move.PreparableMove;
import greycos.solver.core.impl.move.PreparedMoveEvaluation;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;
import greycos.solver.core.preview.api.move.SolutionView;

/** Immutable candidate identity. Provider code runs only through explicit preparation. */
public final class MultistageMoveRequest<Solution_> implements PreparableMove<Solution_> {
  private final MultistageDefinition<Solution_> definition;
  private final long candidateIndex;
  private final long seed;

  public MultistageMoveRequest(
      MultistageDefinition<Solution_> definition, long candidateIndex, long seed) {
    this.definition = Objects.requireNonNull(definition);
    if (candidateIndex < 0)
      throw new IllegalArgumentException("Multistage candidate index must not be negative.");
    this.candidateIndex = candidateIndex;
    this.seed = seed;
  }

  public MultistageDefinition<Solution_> definition() {
    return definition;
  }

  public long candidateIndex() {
    return candidateIndex;
  }

  public long seed() {
    return seed;
  }

  @Override
  public <Score_ extends Score<Score_>> PreparedMoveEvaluation<Solution_, Score_> prepare(
      InnerScoreDirector<Solution_, Score_> director,
      Runnable checkTermination,
      boolean assertFromScratch,
      BiConsumer<SolutionView<Solution_>, Move<Solution_>> finalStateConsumer) {
    long previousCount = director.getCalculationCount();
    var session = definition.session(director);
    long population = session.candidateCount();
    if (candidateIndex >= population)
      throw new IllegalStateException(
          "Multistage candidate index ("
              + candidateIndex
              + ") exceeds the owner provider's candidate count ("
              + population
              + ").");
    var transaction =
        new MultistageTransaction<>(
            director, session.domains, definition.probeLimit, checkTermination);
    var previousSuppression = director.isAllChangesWillBeUndoneBeforeStepEnds();
    PreparedMoveEvaluation.Status status = PreparedMoveEvaluation.Status.EMPTY;
    PreparedMultistageMove<Solution_> frozen = null;
    InnerScore<Score_> score = null;
    Throwable failure = null;
    director.setAllChangesWillBeUndoneBeforeStepEnds(true);
    try {
      director.beforePreparedMove();
      transaction.checkpoint();
      boolean completed = runStages(session, transaction);
      transaction.checkpoint();
      if (completed && transaction.isChanged()) {
        score = transaction.calculateScore();
        if (score.isFullyAssigned() && !score.isStructurallyFlawed()) {
          frozen = transaction.freeze(describe());
          director.afterPreparedMove();
          if (assertFromScratch) director.assertWorkingScoreFromScratch(score, frozen);
          if (finalStateConsumer != null)
            finalStateConsumer.accept(director.getMoveDirector(), frozen);
          transaction.checkpoint();
          status = PreparedMoveEvaluation.Status.EVALUATED;
        } else score = null;
      }
    } catch (MultistageTransaction.ProbeLimitExceeded budget) {
      frozen = null;
      score = null;
    } catch (CancellationException cancellation) {
      status = PreparedMoveEvaluation.Status.CANCELLED;
      frozen = null;
      score = null;
    } catch (RuntimeException | Error e) {
      failure = e;
      transaction.abort(e);
      throw e;
    } finally {
      try {
        transaction.close(failure);
      } finally {
        director.setAllChangesWillBeUndoneBeforeStepEnds(previousSuppression);
      }
    }
    return new PreparedMoveEvaluation<>(
        status, frozen, score, director.getCalculationCount() - previousCount);
  }

  @SuppressWarnings("unchecked")
  private <Score_ extends Score<Score_>> boolean runStages(
      MultistageDefinition.Session<Solution_> session,
      MultistageTransaction<Solution_, Score_> transaction) {
    var random = new SplittableRandom(seed);
    if (session.providerKind == MultistageDefinition.ProviderKind.CROSS) {
      var provider = (CrossVariableStageProvider<Solution_, Score_>) session.provider;
      var stages =
          List.copyOf(
              Objects.requireNonNull(
                  provider.createStages(candidateIndex, random), "Multistage stages"));
      for (var stage : stages) {
        transaction.checkpoint();
        var evaluator = new DefaultCrossVariableMoveEvaluator<Solution_, Score_>(transaction);
        try {
          if (!applyResult(stage.selectMove(evaluator), evaluator, transaction)) return false;
        } finally {
          evaluator.invalidate();
        }
      }
    } else if (session.providerKind == MultistageDefinition.ProviderKind.BASIC) {
      var provider =
          (BasicVariableStageProvider<Solution_, Object, Object, Score_>) session.provider;
      var stages =
          List.copyOf(
              Objects.requireNonNull(
                  provider.createStages(candidateIndex, random), "Multistage stages"));
      for (var stage : stages) {
        transaction.checkpoint();
        var evaluator =
            new DefaultBasicVariableMoveEvaluator<Solution_, Object, Object, Score_>(transaction);
        try {
          if (!applyResult(stage.selectMove(evaluator), evaluator, transaction)) return false;
        } finally {
          evaluator.invalidate();
        }
      }
    } else {
      var provider =
          (ListVariableStageProvider<Solution_, Object, Object, Score_>) session.provider;
      var stages =
          List.copyOf(
              Objects.requireNonNull(
                  provider.createStages(candidateIndex, random), "Multistage stages"));
      for (var stage : stages) {
        transaction.checkpoint();
        var evaluator =
            new DefaultListVariableMoveEvaluator<Solution_, Object, Object, Score_>(transaction);
        try {
          if (!applyResult(stage.selectMove(evaluator), evaluator, transaction)) return false;
        } finally {
          evaluator.invalidate();
        }
      }
    }
    return true;
  }

  private <Score_ extends Score<Score_>> boolean applyResult(
      MultistageStageResult<Solution_> result,
      AbstractMultistageEvaluator<Solution_, Score_> evaluator,
      MultistageTransaction<Solution_, Score_> transaction) {
    transaction.checkpoint();
    return switch (Objects.requireNonNull(
            result, "A multistage stage must return an explicit result.")
        .kind()) {
      case ABORT_CANDIDATE -> false;
      case SKIP -> true;
      case APPLY -> {
        transaction.apply(evaluator.owned(result.operation()));
        yield true;
      }
    };
  }

  @Override
  public void execute(MutableSolutionView<Solution_> solutionView) {
    throw new IllegalStateException(
        "A multistage request must be prepared before execution; only its frozen move can be replayed.");
  }

  @Override
  public Move<Solution_> rebase(Lookup lookup) {
    return new MultistageMoveRequest<>(definition, candidateIndex, seed);
  }

  @Override
  public Object cleanupKey() {
    return definition;
  }

  @Override
  public void closeEvaluationContext(InnerScoreDirector<Solution_, ?> director) {
    definition.closeEvaluationContext(director);
  }

  @Override
  public String describe() {
    return definition.description();
  }

  @Override
  public String toString() {
    return describe() + "[candidate=" + candidateIndex + ", seed=" + seed + "]";
  }
}
