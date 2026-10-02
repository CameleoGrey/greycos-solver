package greycos.solver.core.impl.multistage;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.ListVariableStageProvider;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

/** Selector configuration and owner-local provider sessions, shared safely with move workers. */
public final class MultistageDefinition<Solution_> {
  final GenuineVariableDescriptor<Solution_> variable;
  final long probeLimit;
  private final Class<?> providerClass;
  private final EntityDescriptor<Solution_> targetEntityDescriptor;
  private final Map<InnerScoreDirector<Solution_, ?>, Session<Solution_>> sessions =
      new IdentityHashMap<>();
  private long generation;

  public MultistageDefinition(
      SolutionDescriptor<Solution_> solutionDescriptor,
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      Class<?> providerClass,
      long probeLimit) {
    this(
        solutionDescriptor,
        variableDescriptor,
        providerClass,
        probeLimit,
        variableDescriptor.getEntityDescriptor());
  }

  public MultistageDefinition(
      SolutionDescriptor<Solution_> solutionDescriptor,
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      Class<?> providerClass,
      long probeLimit,
      EntityDescriptor<Solution_> targetEntityDescriptor) {
    Objects.requireNonNull(solutionDescriptor);
    variable = Objects.requireNonNull(variableDescriptor);
    this.providerClass = Objects.requireNonNull(providerClass);
    this.targetEntityDescriptor = Objects.requireNonNull(targetEntityDescriptor);
    if (!variable
        .getEntityDescriptor()
        .getEntityClass()
        .isAssignableFrom(targetEntityDescriptor.getEntityClass())) {
      throw new IllegalArgumentException(
          "Multistage target entity ("
              + targetEntityDescriptor
              + ") does not declare or inherit variable ("
              + variable
              + ").");
    }
    this.probeLimit = probeLimit;
    if (probeLimit <= 0)
      throw new IllegalArgumentException(
          "Multistage probeLimit (" + probeLimit + ") must be positive.");
    var expected =
        variable instanceof ListVariableDescriptor
            ? ListVariableStageProvider.class
            : BasicVariableStageProvider.class;
    if (!expected.isAssignableFrom(providerClass))
      throw new IllegalArgumentException(
          "Multistage provider class ("
              + providerClass.getName()
              + ") must implement "
              + expected.getSimpleName()
              + ".");
  }

  public long candidateCount(InnerScoreDirector<Solution_, ?> director) {
    var session = session(director);
    long count = session.candidateCount();
    if (count < 0)
      throw new IllegalStateException(
          "Multistage provider ("
              + providerClass.getName()
              + ") returned negative candidate count ("
              + count
              + ").");
    return count;
  }

  /** Only the score director's owner invokes its provider callbacks. */
  Session<Solution_> session(InnerScoreDirector<Solution_, ?> director) {
    Session<Solution_> existing;
    long currentGeneration;
    synchronized (sessions) {
      existing = sessions.get(director);
      currentGeneration = generation;
      if (existing != null
          && existing.generation == currentGeneration
          && existing.solution == director.getWorkingSolution()) return existing;
      sessions.remove(director);
    }
    if (existing != null) existing.close();
    Object provider = ConfigUtils.newInstance(this, "stageProviderClass", providerClass);
    var created =
        new Session<Solution_>(
            provider,
            director.getWorkingSolution(),
            currentGeneration,
            new MultistageDomain<>(targetEntityDescriptor, director.getWorkingSolution()));
    try {
      created.initialize();
    } catch (RuntimeException | Error failure) {
      try {
        created.close();
      } catch (RuntimeException | Error cleanup) {
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
      throw failure;
    }
    synchronized (sessions) {
      sessions.put(director, created);
    }
    return created;
  }

  public void closeEvaluationContext(InnerScoreDirector<Solution_, ?> director) {
    Session<Solution_> session;
    synchronized (sessions) {
      session = sessions.remove(director);
    }
    if (session != null) session.close();
  }

  /** Invalidates sessions at a coordinator phase reset without invoking foreign callbacks. */
  public void invalidateEvaluationContexts() {
    synchronized (sessions) {
      generation++;
    }
  }

  String description() {
    return "MultistageMove(" + variable.getSimpleEntityAndVariableName() + ")";
  }

  static final class Session<S> {
    final Object provider;
    final S solution;
    final long generation;
    final MultistageDomain<S> domain;

    Session(Object provider, S solution, long generation, MultistageDomain<S> domain) {
      this.provider = provider;
      this.solution = solution;
      this.generation = generation;
      this.domain = domain;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void initialize() {
      if (provider instanceof BasicVariableStageProvider basic) basic.initialize(solution);
      else ((ListVariableStageProvider) provider).initialize(solution);
    }

    long candidateCount() {
      return provider instanceof BasicVariableStageProvider<?, ?, ?, ?> basic
          ? basic.getCandidateCount()
          : ((ListVariableStageProvider<?, ?, ?, ?>) provider).getCandidateCount();
    }

    void close() {
      if (provider instanceof BasicVariableStageProvider<?, ?, ?, ?> basic) basic.phaseEnded();
      else ((ListVariableStageProvider<?, ?, ?, ?>) provider).phaseEnded();
    }
  }
}
