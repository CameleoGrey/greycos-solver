package ai.greycos.solver.core.impl.cotwin.solution.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import ai.greycos.solver.core.preview.api.cotwin.metamodel.GenuineEntityMetaModel;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.GenuineVariableMetaModel;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningListVariableMetaModel;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.ShadowEntityMetaModel;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.ShadowVariableMetaModel;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.VariableMetaModel;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlanningSolutionMetaModelTest {

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
