package ai.greycos.solver.migration.v2;

import java.util.List;

import ai.greycos.solver.migration.AbstractRecipe;
import ai.greycos.solver.migration.common.RemoveFieldFromMethodInvocationRecipe;

import org.openrewrite.Recipe;
import org.openrewrite.java.RemoveMethodInvocations;

public class GeneralMethodDeleteInvocationMigrationRecipe extends AbstractRecipe {

  @Override
  public String getDisplayName() {
    return "Remove any calls to methods that no longer exist";
  }

  @Override
  public String getDescription() {
    return "Remove calls to methods that no longer exist.";
  }

  @Override
  public List<Recipe> getRecipeList() {
    return List.of(
        // ConstraintStreamImplType
        new RemoveFieldFromMethodInvocationRecipe(
            "ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig getConstraintStreamImplType()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig getConstraintStreamImplType()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig setConstraintStreamImplType(..)"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig withConstraintStreamImplType(..)"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.test.api.score.stream.ConstraintVerifier withConstraintStreamImplType(..)"),
        // Tabu
        new RemoveFieldFromMethodInvocationRecipe(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getUndoMoveTabuSize()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getUndoMoveTabuSize()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig setUndoMoveTabuSize(..)"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig withUndoMoveTabuSize(..)"),
        new RemoveFieldFromMethodInvocationRecipe(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getFadingUndoMoveTabuSize()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getFadingUndoMoveTabuSize()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig setFadingUndoMoveTabuSize(..)"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig withFadingUndoMoveTabuSize(..)"),
        new RemoveFieldFromMethodInvocationRecipe(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getValueTabuRatio()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getValueTabuRatio()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig setValueTabuRatio(..)"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig withValueTabuRatio(..)"),
        new RemoveFieldFromMethodInvocationRecipe(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getFadingValueTabuRatio()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getFadingValueTabuRatio()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig setFadingValueTabuRatio(..)"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig withFadingValueTabuRatio(..)"),
        // Drools support
        new RemoveFieldFromMethodInvocationRecipe(
            "ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig getScoreDrlList()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig getScoreDrlList()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig setScoreDrlList(..)"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig withScoreDrlList(..)"),
        // Constraint
        new RemoveFieldFromMethodInvocationRecipe(
            "ai.greycos.solver.core.api.score.stream.Constraint getConstraintPackage()"),
        new RemoveFieldFromMethodInvocationRecipe(
            "ai.greycos.solver.core.api.score.stream.Constraint getConstraintId()"),
        new RemoveFieldFromMethodInvocationRecipe(
            "ai.greycos.solver.core.api.score.stream.Constraint getConstraintFactory()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.api.score.stream.Constraint getConstraintPackage()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.api.score.stream.Constraint getConstraintId()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.api.score.stream.Constraint getConstraintFactory()"),
        // ConstraintFactory
        new RemoveFieldFromMethodInvocationRecipe(
            "ai.greycos.solver.core.api.score.stream.ConstraintFactory getDefaultConstraintPackage()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.api.score.stream.ConstraintFactory getDefaultConstraintPackage()"),
        // SolverConfig
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.solver.SolverConfig withConstraintStreamImplType(..)"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.solver.SolverConfig getDomainAccessType()"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.solver.SolverConfig setDomainAccessType(..)"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.solver.SolverConfig withDomainAccessType(..)"),
        new RemoveMethodInvocations(
            "ai.greycos.solver.core.config.solver.SolverConfig determineDomainAccessType()"),
        new RemoveFieldFromMethodInvocationRecipe(
            "ai.greycos.solver.core.config.solver.SolverConfig getDomainAccessType()"),
        new RemoveFieldFromMethodInvocationRecipe(
            "ai.greycos.solver.core.config.solver.SolverConfig determineDomainAccessType()"));
  }
}
