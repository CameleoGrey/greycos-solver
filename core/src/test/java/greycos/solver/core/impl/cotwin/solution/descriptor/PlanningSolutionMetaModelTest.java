package greycos.solver.core.impl.cotwin.solution.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.List;
import java.util.TreeSet;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.preview.api.cotwin.metamodel.GenuineEntityMetaModel;
import greycos.solver.core.preview.api.cotwin.metamodel.GenuineVariableMetaModel;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningListVariableMetaModel;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;
import greycos.solver.core.preview.api.cotwin.metamodel.ShadowEntityMetaModel;
import greycos.solver.core.preview.api.cotwin.metamodel.ShadowVariableMetaModel;
import greycos.solver.core.preview.api.cotwin.metamodel.VariableMetaModel;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar.TestdataAddVarBaseEntity;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar.TestdataAddVarChildEntity;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar.TestdataAddVarSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.shadow.extended.TestdataDeclarativeExtendedBaseValue;
import greycos.solver.core.testcotwin.shadow.extended.TestdataDeclarativeExtendedSolution;
import greycos.solver.core.testcotwin.shadow.extended.TestdataDeclarativeExtendedSubclassValue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlanningSolutionMetaModelTest {

  @Test
  void inheritedBasicVariableIsEqualAcrossEntities() {
    var solutionMetaModel =
        SolutionDescriptor.buildSolutionDescriptor(
                TestdataAddVarSolution.class,
                TestdataAddVarBaseEntity.class,
                TestdataAddVarChildEntity.class)
            .getMetaModel();
    var baseEntityMetaModel = solutionMetaModel.genuineEntity(TestdataAddVarBaseEntity.class);
    var subclassEntityMetaModel = solutionMetaModel.genuineEntity(TestdataAddVarChildEntity.class);
    var baseVariableMetaModel = baseEntityMetaModel.genuineVariable("value");
    var subclassVariableMetaModel = subclassEntityMetaModel.genuineVariable("value");
    var subclassOwnVariableMetaModel = subclassEntityMetaModel.genuineVariable("value2");

    assertInheritedVariableOrdering(
        baseVariableMetaModel, subclassVariableMetaModel, subclassOwnVariableMetaModel);
    assertThat(baseVariableMetaModel.entity()).isSameAs(baseEntityMetaModel);
    assertThat(subclassVariableMetaModel.entity()).isSameAs(subclassEntityMetaModel);
    assertThat(baseVariableMetaModel.toString()).contains("TestdataAddVarBaseEntity.value");
    assertThat(subclassVariableMetaModel.toString()).contains("TestdataAddVarChildEntity.value");
  }

  @Test
  void inheritedListVariableIsEqualAcrossEntities() {
    var solutionMetaModel =
        SolutionDescriptor.buildSolutionDescriptor(
                InheritedListSolution.class, BaseListEntity.class, SubclassListEntity.class)
            .getMetaModel();
    var baseEntityMetaModel = solutionMetaModel.genuineEntity(BaseListEntity.class);
    var subclassEntityMetaModel = solutionMetaModel.genuineEntity(SubclassListEntity.class);
    var baseVariableMetaModel = baseEntityMetaModel.listVariable("valueList");
    var subclassVariableMetaModel = subclassEntityMetaModel.listVariable("valueList");
    var subclassOwnVariableMetaModel = subclassEntityMetaModel.listVariable("otherValueList");

    assertInheritedVariableOrdering(
        baseVariableMetaModel, subclassVariableMetaModel, subclassOwnVariableMetaModel);
    assertThat(baseVariableMetaModel.entity()).isSameAs(baseEntityMetaModel);
    assertThat(subclassVariableMetaModel.entity()).isSameAs(subclassEntityMetaModel);
    assertThat(baseVariableMetaModel.toString()).contains("BaseListEntity.valueList");
    assertThat(subclassVariableMetaModel.toString()).contains("SubclassListEntity.valueList");
  }

  @Test
  void inheritedShadowVariableIsEqualAcrossEntities() {
    var solutionMetaModel =
        TestdataDeclarativeExtendedSolution.buildSolutionDescriptor().getMetaModel();
    var baseEntityMetaModel = solutionMetaModel.entity(TestdataDeclarativeExtendedBaseValue.class);
    var subclassEntityMetaModel =
        solutionMetaModel.entity(TestdataDeclarativeExtendedSubclassValue.class);
    var baseVariableMetaModel = baseEntityMetaModel.variable("previous");
    var subclassVariableMetaModel = subclassEntityMetaModel.variable("previous");
    var subclassOwnVariableMetaModel = subclassEntityMetaModel.variable("codeSum");

    assertInheritedVariableOrdering(
        baseVariableMetaModel, subclassVariableMetaModel, subclassOwnVariableMetaModel);
    assertThat(baseVariableMetaModel.entity()).isSameAs(baseEntityMetaModel);
    assertThat(subclassVariableMetaModel.entity()).isSameAs(subclassEntityMetaModel);
    assertThat(baseVariableMetaModel.toString())
        .contains("TestdataDeclarativeExtendedBaseValue.previous");
    assertThat(subclassVariableMetaModel.toString())
        .contains("TestdataDeclarativeExtendedSubclassValue.previous");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void assertInheritedVariableOrdering(
      VariableMetaModel<?, ?, ?> baseVariableMetaModel,
      VariableMetaModel<?, ?, ?> subclassVariableMetaModel,
      VariableMetaModel<?, ?, ?> subclassOwnVariableMetaModel) {
    assertThat(subclassVariableMetaModel).isEqualTo(baseVariableMetaModel);
    assertThat(baseVariableMetaModel).isEqualTo(subclassVariableMetaModel);
    assertThat(subclassVariableMetaModel).hasSameHashCodeAs(baseVariableMetaModel);
    assertThat(((Comparable) subclassVariableMetaModel).compareTo(baseVariableMetaModel)).isZero();
    assertThat(((Comparable) baseVariableMetaModel).compareTo(subclassVariableMetaModel)).isZero();
    assertThat(subclassVariableMetaModel).isNotEqualTo(subclassOwnVariableMetaModel);
    // Ordinals restart for each declaring entity; comparison must still tell these apart.
    assertThat(
            ((InnerVariableMetaModel<?>) subclassVariableMetaModel)
                .variableDescriptor()
                .getOrdinal())
        .isEqualTo(
            ((InnerVariableMetaModel<?>) subclassOwnVariableMetaModel)
                .variableDescriptor()
                .getOrdinal());
    assertThat(((Comparable) subclassVariableMetaModel).compareTo(subclassOwnVariableMetaModel))
        .isNegative();
    assertThat(((Comparable) subclassOwnVariableMetaModel).compareTo(subclassVariableMetaModel))
        .isPositive();

    var sortedVariables = new TreeSet<VariableMetaModel<?, ?, ?>>();
    sortedVariables.add(subclassVariableMetaModel);
    assertThat(sortedVariables.add(baseVariableMetaModel)).isFalse();
    assertThat(sortedVariables.add(subclassOwnVariableMetaModel)).isTrue();
    assertThat(sortedVariables)
        .containsExactly(subclassVariableMetaModel, subclassOwnVariableMetaModel);
  }

  @PlanningSolution
  public static class InheritedListSolution {

    @PlanningEntityCollectionProperty public List<BaseListEntity> entities;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "valueRange")
    public List<String> values;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "otherValueRange")
    public List<Integer> otherValues;

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class BaseListEntity {

    @PlanningListVariable(valueRangeProviderRefs = "valueRange")
    public List<String> valueList;
  }

  @PlanningEntity
  public static class SubclassListEntity extends BaseListEntity {

    @PlanningListVariable(valueRangeProviderRefs = "otherValueRange")
    public List<Integer> otherValueList;
  }

  @Nested
  @DisplayName("Solution with a basic variable")
  class BasicVariablePlanningSolutionMetaModelTest {

    private final SolutionDescriptor<TestdataSolution> solutionDescriptor =
        TestdataSolution.buildSolutionDescriptor();
    private final PlanningSolutionMetaModel<TestdataSolution> planningSolutionMetaModel =
        solutionDescriptor.getMetaModel();

    @Test
    void hasProperType() {
      assertThat(planningSolutionMetaModel.type()).isEqualTo(TestdataSolution.class);
    }

    @Test
    void hasProperEntities() {
      assertThat(planningSolutionMetaModel.entities())
          .containsOnly(planningSolutionMetaModel.entity(TestdataEntity.class));
    }

    @Test
    void hasProperGenuineEntities() {
      assertThat(planningSolutionMetaModel.genuineEntities())
          .containsOnly(planningSolutionMetaModel.genuineEntity(TestdataEntity.class));
    }

    @Test
    void failsOnWrongEntities() {
      assertSoftly(
          softly -> {
            softly
                .assertThatThrownBy(
                    () -> planningSolutionMetaModel.entity(TestdataListEntity.class))
                .hasMessageContaining(TestdataListEntity.class.getSimpleName());
            softly
                .assertThatThrownBy(() -> planningSolutionMetaModel.entity(TestdataValue.class))
                .hasMessageContaining(TestdataValue.class.getSimpleName());
          });
    }

    @Nested
    @DisplayName("with a genuine entity")
    class BasicVariablePlanningEntityMetaModelTest {

      private final GenuineEntityMetaModel<TestdataSolution, TestdataEntity>
          planningEntityMetaModel = planningSolutionMetaModel.genuineEntity(TestdataEntity.class);

      @Test
      void hasProperParent() {
        assertThat(planningEntityMetaModel.solution()).isSameAs(planningSolutionMetaModel);
      }

      @Test
      void hasProperType() {
        assertThat(planningEntityMetaModel.type()).isEqualTo(TestdataEntity.class);
      }

      @Test
      void isGenuine() {
        assertThat(planningEntityMetaModel.isGenuine()).isTrue();
      }

      @Test
      void hasProperVariables() {
        var variableMetaModel =
            (GenuineVariableMetaModel<TestdataSolution, TestdataEntity, ?>)
                planningEntityMetaModel.variable("value");
        assertSoftly(
            softly -> {
              softly.assertThat(planningEntityMetaModel.variables()).hasSize(1);
              softly
                  .assertThat(planningEntityMetaModel.variables().get(0))
                  .isEqualTo(variableMetaModel);
              softly
                  .assertThat(planningEntityMetaModel.genuineVariables())
                  .containsOnly(variableMetaModel);
            });
      }

      @Test
      void failsOnNonExistingVariable() {
        assertThatThrownBy(() -> planningEntityMetaModel.variable("nonExisting"))
            .hasMessageContaining("nonExisting");
      }

      @Nested
      @DisplayName("with a genuine variable")
      class PlanningVariableMetaModelTest {

        private final VariableMetaModel<TestdataSolution, TestdataEntity, TestdataValue>
            variableMetaModel = planningEntityMetaModel.variable("value");

        @Test
        void hasProperParent() {
          assertThat(variableMetaModel.entity()).isSameAs(planningEntityMetaModel);
        }

        @Test
        void hasProperType() {
          assertThat(variableMetaModel.type()).isEqualTo(TestdataValue.class);
        }
      }
    }
  }

  @Nested
  @DisplayName("Solution with a list variable")
  class ListVariablePlanningSolutionMetaModelTest {

    private final SolutionDescriptor<TestdataListSolution> solutionDescriptor =
        TestdataListSolution.buildSolutionDescriptor();
    private final PlanningSolutionMetaModel<TestdataListSolution> planningSolutionMetaModel =
        solutionDescriptor.getMetaModel();

    @Test
    void hasProperType() {
      assertThat(planningSolutionMetaModel.type()).isEqualTo(TestdataListSolution.class);
    }

    @Test
    void hasProperEntities() {
      assertThat(planningSolutionMetaModel.entities())
          .containsExactly(
              planningSolutionMetaModel.entity(TestdataListEntity.class),
              planningSolutionMetaModel.entity(TestdataListValue.class));
    }

    @Test
    void hasProperGenuineEntities() {
      assertThat(planningSolutionMetaModel.genuineEntities())
          .containsOnly(planningSolutionMetaModel.genuineEntity(TestdataListEntity.class));
    }

    @Test
    void failsOnWrongEntities() {
      assertSoftly(
          softly -> {
            softly
                .assertThatThrownBy(() -> planningSolutionMetaModel.entity(TestdataEntity.class))
                .hasMessageContaining(TestdataEntity.class.getSimpleName());
            softly
                .assertThatThrownBy(() -> planningSolutionMetaModel.entity(TestdataValue.class))
                .hasMessageContaining(TestdataValue.class.getSimpleName());
          });
    }

    @Nested
    @DisplayName("with a genuine entity")
    class GenuinePlanningEntityMetaModelTest {

      private final GenuineEntityMetaModel<TestdataListSolution, TestdataListEntity>
          planningEntityMetaModel =
              planningSolutionMetaModel.genuineEntity(TestdataListEntity.class);

      @Test
      void hasProperParent() {
        assertThat(planningEntityMetaModel.solution()).isSameAs(planningSolutionMetaModel);
      }

      @Test
      void hasProperType() {
        assertThat(planningEntityMetaModel.type()).isEqualTo(TestdataListEntity.class);
      }

      @Test
      void isGenuine() {
        assertThat(planningEntityMetaModel.isGenuine()).isTrue();
      }

      @Test
      void hasProperVariables() {
        var variableMetaModel =
            (GenuineVariableMetaModel<TestdataListSolution, TestdataListEntity, ?>)
                planningEntityMetaModel.variable("valueList");
        assertSoftly(
            softly -> {
              softly.assertThat(planningEntityMetaModel.variables()).hasSize(1);
              softly
                  .assertThat(planningEntityMetaModel.variables().get(0))
                  .isEqualTo(variableMetaModel);
              softly
                  .assertThat(planningEntityMetaModel.genuineVariables())
                  .containsOnly(variableMetaModel);
            });
      }

      @Test
      void failsOnNonExistingVariable() {
        assertThatThrownBy(() -> planningEntityMetaModel.variable("nonExisting"))
            .hasMessageContaining("nonExisting");
      }

      @Nested
      @DisplayName("with a genuine variable")
      class PlanningListVariableMetaModelTest {

        private final PlanningListVariableMetaModel<
                TestdataListSolution, TestdataListEntity, TestdataListValue>
            variableMetaModel =
                (PlanningListVariableMetaModel<
                        TestdataListSolution, TestdataListEntity, TestdataListValue>)
                    planningEntityMetaModel.<TestdataListValue>variable("valueList");

        @Test
        void hasProperParent() {
          assertThat(variableMetaModel.entity()).isSameAs(planningEntityMetaModel);
        }

        @Test
        void hasProperType() {
          assertThat(variableMetaModel.type()).isEqualTo(TestdataListValue.class);
        }
      }
    }

    @Nested
    @DisplayName("with a shadow entity")
    class ShadowPlanningEntityMetaModelTest {

      private final ShadowEntityMetaModel<TestdataListSolution, TestdataListValue>
          planningEntityMetaModel = planningSolutionMetaModel.shadowEntity(TestdataListValue.class);

      @Test
      void hasProperParent() {
        assertThat(planningEntityMetaModel.solution()).isSameAs(planningSolutionMetaModel);
      }

      @Test
      void hasProperType() {
        assertThat(planningEntityMetaModel.type()).isEqualTo(TestdataListValue.class);
      }

      @Test
      void isNotGenuine() {
        assertThat(planningEntityMetaModel.isGenuine()).isFalse();
      }

      @Test
      void hasProperVariables() {
        var genuineVariableMetaModel =
            planningEntityMetaModel.<TestdataListEntity>variable("entity");
        var shadowVariableMetaModel = planningEntityMetaModel.<Integer>variable("index");
        assertSoftly(
            softly -> {
              softly
                  .assertThat(planningEntityMetaModel.variables())
                  .containsExactly(genuineVariableMetaModel, shadowVariableMetaModel);
            });
      }

      @Test
      void failsOnNonExistingVariable() {
        assertThatThrownBy(() -> planningEntityMetaModel.variable("nonExisting"))
            .hasMessageContaining("nonExisting");
      }

      @Nested
      @DisplayName("with a shadow variable")
      class PlanningListVariableMetaModelTest {

        private final ShadowVariableMetaModel<
                TestdataListSolution, TestdataListValue, TestdataListEntity>
            variableMetaModel =
                (ShadowVariableMetaModel<
                        TestdataListSolution, TestdataListValue, TestdataListEntity>)
                    planningEntityMetaModel.<TestdataListEntity>variable("entity");

        @Test
        void hasProperParent() {
          assertThat(variableMetaModel.entity()).isSameAs(planningEntityMetaModel);
        }

        @Test
        void hasProperType() {
          assertThat(variableMetaModel.type()).isEqualTo(TestdataListEntity.class);
        }
      }
    }
  }
}
