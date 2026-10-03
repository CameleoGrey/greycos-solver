package greycos.solver.core.impl.multistage;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.api.solver.multistage.BasicVariableReference;
import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.CrossVariableStageProvider;
import greycos.solver.core.api.solver.multistage.ListVariableReference;
import greycos.solver.core.api.solver.multistage.ListVariableStageProvider;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

/** Selector configuration and owner-local provider sessions, shared safely with move workers. */
public final class MultistageDefinition<Solution_> {
  final List<MultistageVariableBinding<Solution_>> bindings;
  final long probeLimit;
  private final Class<?> providerClass;
  private final ProviderKind providerKind;
  private final String description;
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
    this(
        solutionDescriptor,
        List.of(legacyBinding(variableDescriptor, targetEntityDescriptor)),
        providerClass,
        probeLimit,
        false);
  }

  public MultistageDefinition(
      SolutionDescriptor<Solution_> solutionDescriptor,
      List<MultistageVariableBinding<Solution_>> bindings,
      Class<?> providerClass,
      long probeLimit) {
    this(solutionDescriptor, bindings, providerClass, probeLimit, true);
  }

  private MultistageDefinition(
      SolutionDescriptor<Solution_> solutionDescriptor,
      List<MultistageVariableBinding<Solution_>> bindings,
      Class<?> providerClass,
      long probeLimit,
      boolean crossVariable) {
    Objects.requireNonNull(solutionDescriptor);
    this.bindings = List.copyOf(bindings);
    if (this.bindings.isEmpty()) {
      throw new IllegalArgumentException(
          "A multistage definition must declare at least one variable.");
    }
    var references = new java.util.HashSet<>();
    for (var binding : this.bindings) {
      var reference = binding.reference();
      var target = binding.targetEntityDescriptor();
      if (target.getSolutionDescriptor() != solutionDescriptor
          || target.getGenuineVariableDescriptor(reference.variableName()) != binding.variable()
          || reference.entityClass() != target.getEntityClass()
          || (reference instanceof ListVariableReference) != binding.variable().isListVariable()) {
        throw new IllegalArgumentException(
            "Multistage variable binding ("
                + binding
                + ") does not match its reference and solution descriptor.");
      }
      var valueClass =
          binding.variable() instanceof ListVariableDescriptor<Solution_> list
              ? list.getElementType()
              : binding.variable().getVariablePropertyType();
      if (reference.valueClass() != valueClass) {
        throw new IllegalArgumentException(
            "Multistage variable reference ("
                + reference
                + ") must use the variable's exact value type ("
                + valueClass.getName()
                + ").");
      }
      if (!references.add(reference)) {
        throw new IllegalArgumentException(
            "Duplicate multistage variable reference (" + reference + ").");
      }
    }
    var variable = this.bindings.getFirst().variable();
    this.providerClass = Objects.requireNonNull(providerClass);
    this.probeLimit = probeLimit;
    if (probeLimit <= 0)
      throw new IllegalArgumentException(
          "Multistage probeLimit (" + probeLimit + ") must be positive.");
    providerKind =
        crossVariable
            ? ProviderKind.CROSS
            : variable instanceof ListVariableDescriptor ? ProviderKind.LIST : ProviderKind.BASIC;
    var expected =
        switch (providerKind) {
          case CROSS -> CrossVariableStageProvider.class;
          case LIST -> ListVariableStageProvider.class;
          case BASIC -> BasicVariableStageProvider.class;
        };
    description =
        "MultistageMove("
            + this.bindings.stream()
                .map(binding -> binding.variable().getSimpleEntityAndVariableName())
                .collect(java.util.stream.Collectors.joining(", "))
            + ")";
    if (!expected.isAssignableFrom(providerClass))
      throw new IllegalArgumentException(
          "Multistage provider class ("
              + providerClass.getName()
              + ") must implement "
              + expected.getSimpleName()
              + ".");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static <S> MultistageVariableBinding<S> legacyBinding(
      GenuineVariableDescriptor<S> variable, EntityDescriptor<S> target) {
    var reference =
        variable instanceof ListVariableDescriptor<S> list
            ? ListVariableReference.of(
                (Class) target.getEntityClass(),
                variable.getVariableName(),
                (Class) list.getElementType())
            : BasicVariableReference.of(
                (Class) target.getEntityClass(),
                variable.getVariableName(),
                (Class) variable.getVariablePropertyType());
    return new MultistageVariableBinding<>(reference, variable, target);
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
            providerKind,
            director.getWorkingSolution(),
            currentGeneration,
            domains(director.getWorkingSolution()));
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

  private Map<MultistageVariableBinding<Solution_>, MultistageDomain<Solution_>> domains(
      Solution_ solution) {
    var result =
        new LinkedHashMap<MultistageVariableBinding<Solution_>, MultistageDomain<Solution_>>();
    var scopes = new IdentityHashMap<EntityDescriptor<Solution_>, MultistageDomain<Solution_>>();
    for (var binding : bindings) {
      result.put(
          binding,
          scopes.computeIfAbsent(
              binding.targetEntityDescriptor(),
              target -> new MultistageDomain<>(target, solution)));
    }
    return java.util.Collections.unmodifiableMap(result);
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
    return description;
  }

  enum ProviderKind {
    BASIC,
    LIST,
    CROSS
  }

  static final class Session<S> {
    final Object provider;
    final ProviderKind providerKind;
    final S solution;
    final long generation;
    final Map<MultistageVariableBinding<S>, MultistageDomain<S>> domains;

    Session(
        Object provider,
        ProviderKind providerKind,
        S solution,
        long generation,
        Map<MultistageVariableBinding<S>, MultistageDomain<S>> domains) {
      this.provider = provider;
      this.providerKind = providerKind;
      this.solution = solution;
      this.generation = generation;
      this.domains = domains;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void initialize() {
      switch (providerKind) {
        case CROSS -> ((CrossVariableStageProvider) provider).initialize(solution);
        case BASIC -> ((BasicVariableStageProvider) provider).initialize(solution);
        case LIST -> ((ListVariableStageProvider) provider).initialize(solution);
      }
    }

    long candidateCount() {
      return switch (providerKind) {
        case CROSS -> ((CrossVariableStageProvider<?, ?>) provider).getCandidateCount();
        case BASIC -> ((BasicVariableStageProvider<?, ?, ?, ?>) provider).getCandidateCount();
        case LIST -> ((ListVariableStageProvider<?, ?, ?, ?>) provider).getCandidateCount();
      };
    }

    void close() {
      switch (providerKind) {
        case CROSS -> ((CrossVariableStageProvider<?, ?>) provider).phaseEnded();
        case BASIC -> ((BasicVariableStageProvider<?, ?, ?, ?>) provider).phaseEnded();
        case LIST -> ((ListVariableStageProvider<?, ?, ?, ?>) provider).phaseEnded();
      }
    }
  }
}
