package greycos.solver.core.impl.move.builtin;

import java.util.Arrays;
import java.util.List;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.move.MoveDirector;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningVariableMetaModel;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.sample.Sample;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingScoreCalculator;
import greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingSolution;

final class SampleValueRangesTestSupport implements AutoCloseable {

  final TestdataValue a = new StableHashValue("a");
  final TestdataValue b = new StableHashValue("b");
  final TestdataValue c = new StableHashValue("c");
  final TestdataValue d = new StableHashValue("d");
  final TestdataAllowsUnassignedEntityProvidingEntity entityA =
      new TestdataAllowsUnassignedEntityProvidingEntity("A", List.of(a, b, c));
  final TestdataAllowsUnassignedEntityProvidingEntity duplicateA =
      new TestdataAllowsUnassignedEntityProvidingEntity("A2", List.of(a, b, c));
  final TestdataAllowsUnassignedEntityProvidingEntity entityB =
      new TestdataAllowsUnassignedEntityProvidingEntity("B", List.of(c, b, d));
  final TestdataAllowsUnassignedEntityProvidingEntity smaller =
      new TestdataAllowsUnassignedEntityProvidingEntity("smaller", List.of(b, c));
  final TestdataAllowsUnassignedEntityProvidingEntity duplicateSmaller =
      new TestdataAllowsUnassignedEntityProvidingEntity("smaller2", List.of(b, c));

  private final InnerScoreDirector<TestdataAllowsUnassignedEntityProvidingSolution, SimpleScore>
      scoreDirector;
  private final MoveDirector<TestdataAllowsUnassignedEntityProvidingSolution, SimpleScore>
      moveDirector;
  private final PlanningVariableMetaModel<
          TestdataAllowsUnassignedEntityProvidingSolution,
          TestdataAllowsUnassignedEntityProvidingEntity,
          TestdataValue>
      variableMetaModel;

  SampleValueRangesTestSupport() {
    var solutionDescriptor =
        TestdataAllowsUnassignedEntityProvidingSolution.buildSolutionDescriptor();
    variableMetaModel =
        solutionDescriptor
            .getMetaModel()
            .genuineEntity(TestdataAllowsUnassignedEntityProvidingEntity.class)
            .basicVariable();
    scoreDirector =
        new EasyScoreDirectorFactory<>(
                solutionDescriptor,
                new TestdataAllowsUnassignedEntityProvidingScoreCalculator(),
                EnvironmentMode.PHASE_ASSERT)
            .buildScoreDirector();
    var solution = new TestdataAllowsUnassignedEntityProvidingSolution("s");
    solution.setEntityList(List.of(entityA, duplicateA, entityB, smaller, duplicateSmaller));
    scoreDirector.setWorkingSolution(solution);
    moveDirector = new MoveDirector<>(scoreDirector);
  }

  SampleValueRanges<TestdataValue> ranges(
      TestdataAllowsUnassignedEntityProvidingEntity... entities) {
    return SampleValueRanges.of(
        Sample.of(Arrays.asList(entities)), variableMetaModel, moveDirector);
  }

  ValueRange<TestdataValue> rangeOf(TestdataAllowsUnassignedEntityProvidingEntity entity) {
    return moveDirector.getValueRange(variableMetaModel, entity);
  }

  @Override
  public void close() {
    scoreDirector.close();
  }

  /** Keep the fixtures' range hashes stable across JVMs, independently of object allocation. */
  private static final class StableHashValue extends TestdataValue {

    private StableHashValue(String code) {
      super(code);
    }

    @Override
    public int hashCode() {
      return getCode().hashCode();
    }
  }
}
