package greycos.solver.core.impl.geneticalgorithm;

import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;

/** Describes one completed seeding or offspring attempt, including cached and invalid trials. */
public final class GeneticAlgorithmStepScope<Solution_> extends AbstractStepScope<Solution_> {
  private final GeneticAlgorithmPhaseScope<Solution_> phaseScope;
  private GeneticAlgorithmOutcome outcome;
  private GeneticAlgorithmMutationType mutationType;
  private String mutationGroup;
  private boolean seeding;
  private boolean crossed;
  private long generation;
  private long candidateId;
  private long firstParentId = -1;
  private long secondParentId = -1;
  private long nativeId = -1;
  private boolean admitted;
  private int changedAssignmentCount;
  private InnerScore<?> beforeScore;
  private InnerScore<?> bestBeforeScore;
  private InnerScore<?> candidateScore;

  public GeneticAlgorithmStepScope(GeneticAlgorithmPhaseScope<Solution_> phaseScope) {
    this(phaseScope, phaseScope.getNextStepIndex());
  }

  public GeneticAlgorithmStepScope(GeneticAlgorithmPhaseScope<Solution_> phaseScope, int index) {
    super(index);
    this.phaseScope = phaseScope;
  }

  @Override
  public GeneticAlgorithmPhaseScope<Solution_> getPhaseScope() {
    return phaseScope;
  }

  public GeneticAlgorithmOutcome getOutcome() {
    return outcome;
  }

  public void setOutcome(GeneticAlgorithmOutcome outcome) {
    this.outcome = outcome;
  }

  public GeneticAlgorithmMutationType getMutationType() {
    return mutationType;
  }

  public void setMutationType(GeneticAlgorithmMutationType type) {
    mutationType = type;
  }

  public String getMutationGroup() {
    return mutationGroup;
  }

  public void setMutationGroup(String group) {
    mutationGroup = group;
  }

  public boolean isSeeding() {
    return seeding;
  }

  public void setSeeding(boolean seeding) {
    this.seeding = seeding;
  }

  public boolean isCrossed() {
    return crossed;
  }

  public void setCrossed(boolean crossed) {
    this.crossed = crossed;
  }

  public long getGeneration() {
    return generation;
  }

  public void setGeneration(long generation) {
    this.generation = generation;
  }

  public long getCandidateId() {
    return candidateId;
  }

  public void setCandidateId(long id) {
    candidateId = id;
  }

  public long getFirstParentId() {
    return firstParentId;
  }

  public long getSecondParentId() {
    return secondParentId;
  }

  public void setParentIds(long first, long second) {
    firstParentId = first;
    secondParentId = second;
  }

  public long getNativeId() {
    return nativeId;
  }

  public void setNativeId(long id) {
    nativeId = id;
  }

  public boolean isAdmitted() {
    return admitted;
  }

  public void setAdmitted(boolean admitted) {
    this.admitted = admitted;
  }

  public int getChangedAssignmentCount() {
    return changedAssignmentCount;
  }

  public void setChangedAssignmentCount(int count) {
    changedAssignmentCount = count;
  }

  public InnerScore<?> getBeforeScore() {
    return beforeScore;
  }

  public void setBeforeScore(InnerScore<?> score) {
    beforeScore = score;
  }

  public InnerScore<?> getBestBeforeScore() {
    return bestBeforeScore;
  }

  public void setBestBeforeScore(InnerScore<?> score) {
    bestBeforeScore = score;
  }

  public InnerScore<?> getCandidateScore() {
    return candidateScore;
  }

  public void setCandidateScore(InnerScore<?> score) {
    candidateScore = score;
  }

  public String getMoveTypeDescription() {
    return "GeneticAlgorithm/" + (seeding ? "SEED" : mutationType) + "/" + outcome;
  }
}
