package greycos.solver.migration.v2;

import java.util.List;

import greycos.solver.migration.AbstractRecipe;
import greycos.solver.migration.common.RemoveFieldFromMethodInvocationRecipe;

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
            "greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig getConstraintStreamImplType()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig getConstraintStreamImplType()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig setConstraintStreamImplType(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig withConstraintStreamImplType(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.test.api.score.stream.ConstraintVerifier withConstraintStreamImplType(..)"),
        // Tabu
        new RemoveFieldFromMethodInvocationRecipe(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getUndoMoveTabuSize()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getUndoMoveTabuSize()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig setUndoMoveTabuSize(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig withUndoMoveTabuSize(..)"),
        new RemoveFieldFromMethodInvocationRecipe(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getFadingUndoMoveTabuSize()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getFadingUndoMoveTabuSize()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig setFadingUndoMoveTabuSize(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig withFadingUndoMoveTabuSize(..)"),
        new RemoveFieldFromMethodInvocationRecipe(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getValueTabuRatio()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getValueTabuRatio()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig setValueTabuRatio(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig withValueTabuRatio(..)"),
        new RemoveFieldFromMethodInvocationRecipe(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getFadingValueTabuRatio()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig getFadingValueTabuRatio()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig setFadingValueTabuRatio(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig withFadingValueTabuRatio(..)"),
        // Drools support
        new RemoveFieldFromMethodInvocationRecipe(
            "greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig getScoreDrlList()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig getScoreDrlList()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig setScoreDrlList(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig withScoreDrlList(..)"),
        // Constraint
        new RemoveFieldFromMethodInvocationRecipe(
            "greycos.solver.core.api.score.stream.Constraint getConstraintPackage()"),
        new RemoveFieldFromMethodInvocationRecipe(
            "greycos.solver.core.api.score.stream.Constraint getConstraintId()"),
        new RemoveFieldFromMethodInvocationRecipe(
            "greycos.solver.core.api.score.stream.Constraint getConstraintFactory()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.api.score.stream.Constraint getConstraintPackage()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.api.score.stream.Constraint getConstraintId()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.api.score.stream.Constraint getConstraintFactory()"),
        // ConstraintFactory
        new RemoveFieldFromMethodInvocationRecipe(
            "greycos.solver.core.api.score.stream.ConstraintFactory getDefaultConstraintPackage()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.api.score.stream.ConstraintFactory getDefaultConstraintPackage()"),
        // SolverConfig
        new RemoveMethodInvocations(
            "greycos.solver.core.config.solver.SolverConfig withConstraintStreamImplType(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.solver.SolverConfig getDomainAccessType()"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.solver.SolverConfig setDomainAccessType(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.solver.SolverConfig withDomainAccessType(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.core.config.solver.SolverConfig determineDomainAccessType()"),
        new RemoveFieldFromMethodInvocationRecipe(
            "greycos.solver.core.config.solver.SolverConfig getDomainAccessType()"),
        new RemoveFieldFromMethodInvocationRecipe(
            "greycos.solver.core.config.solver.SolverConfig determineDomainAccessType()"),
        // Indictments are no longer part of the public score-analysis model.
        new RemoveMethodInvocations(
            "greycos.solver.core.api.score.stream.uni.UniConstraintBuilder indictWith(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.core.api.score.stream.bi.BiConstraintBuilder indictWith(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.core.api.score.stream.tri.TriConstraintBuilder indictWith(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.core.api.score.stream.quad.QuadConstraintBuilder indictWith(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.test.api.score.stream.SingleConstraintAssertion indictsWith(..)"),
        new RemoveMethodInvocations(
            "greycos.solver.test.api.score.stream.SingleConstraintAssertion indictsWithExactly(..)"));
  }
}
