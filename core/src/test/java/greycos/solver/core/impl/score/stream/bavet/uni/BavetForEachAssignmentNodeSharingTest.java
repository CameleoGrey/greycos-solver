package greycos.solver.core.impl.score.stream.bavet.uni;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.api.score.stream.uni.UniConstraintStream;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.list.pinned.noshadows.TestdataPinnedNoShadowsListSolution;
import greycos.solver.core.testcotwin.list.pinned.noshadows.TestdataPinnedNoShadowsListValue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BavetForEachAssignmentNodeSharingTest {

  static Stream<Arguments> declarationOrdersAndPolicies() {
    return Stream.of(false, true)
        .flatMap(
            explicitFirst ->
                Stream.of(ConstraintMatchPolicy.values())
                    .map(policy -> Arguments.of(explicitFirst, policy)));
  }

  @ParameterizedTest
  @MethodSource("declarationOrdersAndPolicies")
  @SuppressWarnings("unchecked")
  void implicitAssignmentAndExplicitExistenceKeepSeparateSharingAndLineage(
      boolean explicitFirst, ConstraintMatchPolicy policy) {
    var descriptor = TestdataPinnedNoShadowsListSolution.buildSolutionDescriptor();
    var listDescriptor = descriptor.getListVariableDescriptorList().getFirst();
    var solution = TestdataPinnedNoShadowsListSolution.generateInitializedSolution(1, 1);
    var value = solution.getValueList().getFirst();
    var owner = solution.getEntityList().getFirst();
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<
            TestdataPinnedNoShadowsListSolution, SimpleScore>(
            descriptor,
            streams -> {
              var source =
                  streams.forEachIncludingUnassigned(TestdataPinnedNoShadowsListValue.class);
              var owners =
                  streams.forEachUnfiltered(
                      (Class<Object>) listDescriptor.getEntityDescriptor().getEntityClass());
              // Use exactly the predicate instance used by the factory's implicit filter.
              var joiner =
                  Joiners.filtering(
                      listDescriptor.<TestdataPinnedNoShadowsListValue>getInListPredicate());
              UniConstraintStream<TestdataPinnedNoShadowsListValue> assigned;
              UniConstraintStream<TestdataPinnedNoShadowsListValue> explicit;
              if (explicitFirst) {
                explicit = source.ifExists(owners, joiner);
                assigned = streams.forEach(TestdataPinnedNoShadowsListValue.class);
              } else {
                assigned = streams.forEach(TestdataPinnedNoShadowsListValue.class);
                explicit = source.ifExists(owners, joiner);
              }
              assertThat(assigned).isNotSameAs(explicit).isNotEqualTo(explicit);
              assertThat(streams.forEach(TestdataPinnedNoShadowsListValue.class))
                  .isSameAs(assigned);
              assertThat(asBavet(source).ifExistsForEachAssignment(owners, joiner))
                  .isSameAs(assigned);
              assertThat(source.ifExists(owners, joiner)).isSameAs(explicit);

              var assignedExists = (BavetIfExistsUniConstraintStream<?, ?, ?>) assigned;
              var explicitExists = (BavetIfExistsUniConstraintStream<?, ?, ?>) explicit;
              assertThat(assignedExists.isForEachAssignmentFilter()).isTrue();
              assertThat(explicitExists.isForEachAssignmentFilter()).isFalse();
              assertThat(assignedExists.getLeftParent()).isSameAs(source);
              assertThat(explicitExists.getLeftParent()).isSameAs(source);
              assertThat(assignedExists.getRightParent().getParent()).isSameAs(owners);
              assertThat(explicitExists.getRightParent().getParent()).isSameAs(owners);

              // Aliasing must retain the logical input's explicit witness lineage, not merely
              // the original physical tuple source's lineage.
              var assignmentAfterExplicit =
                  asBavet(explicit).ifExistsForEachAssignment(owners, joiner);
              return new Constraint[] {
                source.penalize(SimpleScore.ONE).asConstraint("source"),
                assigned.penalize(SimpleScore.ONE).asConstraint("assigned"),
                explicit.penalize(SimpleScore.ONE).asConstraint("explicit"),
                assignmentAfterExplicit
                    .penalize(SimpleScore.ONE)
                    .asConstraint("assignmentAfterExplicit")
              };
            },
            EnvironmentMode.NO_ASSERT);
    try (var director =
        factory.createScoreDirectorBuilder().withConstraintMatchPolicy(policy).build()) {
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-4));
      var maps = director.getIndictmentMaps();
      if (policy.getIndictmentFetchPolicy() == IndictmentFetchPolicy.NONE) {
        assertThat(maps.terminal()).isNull();
      } else {
        assertThat(maps.terminal()).containsOnlyKeys(value);
        assertThat(maps.terminal().get(value).matchCount()).isEqualTo(4);
      }
      if (policy.getIndictmentFetchPolicy() == IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL) {
        assertThat(maps.original()).containsOnlyKeys(value, owner);
        assertThat(maps.original().get(value).matchCount()).isEqualTo(4);
        assertThat(maps.original().get(owner).matches())
            .extracting(match -> match.constraintRef().id())
            .containsExactlyInAnyOrder("explicit", "assignmentAfterExplicit");
      } else {
        assertThat(maps.original()).isNull();
      }
      if (policy.isMatchOccurrenceEnabled()) {
        var raw = director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL).rawAnalysis();
        assertThat(raw.matchOccurrences())
            .hasSize(4)
            .allSatisfy(
                occurrence -> {
                  assertThat(occurrence.indictedObjects()).containsExactly(value);
                  if (policy.getIndictmentFetchPolicy()
                      != IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL) {
                    assertThat(occurrence.originalIndictedObjects()).isNull();
                  } else if (occurrence.constraintRef().id().equals("explicit")
                      || occurrence.constraintRef().id().equals("assignmentAfterExplicit")) {
                    assertThat(occurrence.originalIndictedObjects())
                        .containsExactlyInAnyOrder(value, owner);
                  } else {
                    assertThat(occurrence.originalIndictedObjects()).containsExactly(value);
                  }
                });
      }
    }
  }

  private static <A> BavetAbstractUniConstraintStream<?, A> asBavet(UniConstraintStream<A> stream) {
    return (BavetAbstractUniConstraintStream<?, A>) stream;
  }
}
