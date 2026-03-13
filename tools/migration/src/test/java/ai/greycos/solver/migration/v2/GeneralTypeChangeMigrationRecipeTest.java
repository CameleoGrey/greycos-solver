package ai.greycos.solver.migration.v2;

import static org.openrewrite.java.Assertions.java;

import java.util.List;

import ai.greycos.solver.migration.NoWildCardImportStyle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

@Execution(ExecutionMode.CONCURRENT)
class GeneralTypeChangeMigrationRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new GeneralTypeChangeMigrationRecipe())
        .typeValidationOptions(TypeValidation.builder().allowMissingType(ignore -> true).build())
        .parser(
            JavaParser.fromJavaVersion()
                .styles(List.of(new NoWildCardImportStyle()))
                // We must add all old classes as stubs to the JavaTemplate
                .dependsOn(
                    "package ai.greycos.solver.core.api.domain.lookup; public class PlanningId {}",
                    "package ai.greycos.solver.core.api.score.director; public class ScoreDirector {}",
                    "package ai.greycos.solver.core.api.score.buildin.simple; public class SimpleScore {}",
                    "package ai.greycos.solver.core.api.score.buildin.simplelong; public class SimpleLongScore {}",
                    "package ai.greycos.solver.core.api.score.buildin.simplebigdecimal; public class SimpleBigDecimalScore {}",
                    "package ai.greycos.solver.core.api.score.buildin.hardsoft; public class HardSoftScore {}",
                    "package ai.greycos.solver.core.api.score.buildin.hardsoftlong; public class HardSoftLongScore {}",
                    "package ai.greycos.solver.core.api.score.buildin.hardsoftbigdecimal; public class HardSoftBigDecimalScore {}",
                    "package ai.greycos.solver.core.api.score.buildin.hardmediumsoft; public class HardMediumSoftScore {}",
                    "package ai.greycos.solver.core.api.score.buildin.hardmediumsoftlong; public class HardMediumSoftLongScore {}",
                    "package ai.greycos.solver.core.api.score.buildin.hardmediumsoftbigdecimal; public class HardMediumSoftBigDecimalScore {}",
                    "package ai.greycos.solver.core.api.score.buildin.bendable; public class BendableScore {}",
                    "package ai.greycos.solver.core.api.score.buildin.bendablelong; public class BendableLongScore {}",
                    "package ai.greycos.solver.core.api.score.buildin.bendablebigdecimal; public class BendableBigDecimalScore {}",
                    "package ai.greycos.solver.core.api.solver; public class ProblemFactChange {}",
                    "package ai.greycos.solver.core.api.domain.valuerange; public class CountableValueRange {}",
                    "package ai.greycos.solver.core.api.domain.valuerange; public class ValueRange {}",
                    "package ai.greycos.solver.core.impl.domain.valuerange.buildin.composite; public class CompositeCountableValueRange {}",
                    "package ai.greycos.solver.core.impl.domain.valuerange.buildin.composite; public class NullAllowingCountableValueRange {}",
                    "package ai.greycos.solver.core.impl.heuristic.move; public interface Move {}",
                    "package ai.greycos.solver.core.impl.heuristic.move; public interface AbstractMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.move; public interface NoChangeMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.move; public interface CompositeMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt; public interface KOptListMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt; public interface TwoOptListMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin; public interface ListRuinRecreateMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface ListAssignMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface ListChangeMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface ListSwapMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface ListUnassignMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface SubListChangeMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface SubListSwapMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface SubListUnassignMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic; public interface ChangeMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic; public interface PillarChangeMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic; public interface PillarSwapMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic; public interface RuinRecreateMove {}",
                    "package ai.greycos.solver.core.impl.heuristic.selector.move.generic; public interface SwapMove {}"));
  }

  @Test
  void migrate() {
    rewriteRun(
        java(
            """
                        package greycos;

                        import ai.greycos.solver.core.api.domain.lookup.PlanningId;
                        import ai.greycos.solver.core.api.solver.ProblemFactChange;

                        public class Test {
                                @PlanningId
                                SimpleScore simpleScore;
                                ProblemFactChange  problemFactChange;
                        }""",
            """
                        package greycos;

                        import ai.greycos.solver.core.api.domain.common.PlanningId;
                        import ai.greycos.solver.core.api.solver.change.ProblemChange;

                        public class Test {
                                @PlanningId
                                SimpleScore simpleScore;
                                ProblemChange  problemFactChange;
                        }"""));
  }

  @Test
  void migrateScore() {
    rewriteRun(
        java(
            """
                        package greycos;

                        import ai.greycos.solver.core.api.score.director.ScoreDirector;
                        import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
                        import ai.greycos.solver.core.api.score.buildin.simplelong.SimpleLongScore;
                        import ai.greycos.solver.core.api.score.buildin.simplebigdecimal.SimpleBigDecimalScore;
                        import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;
                        import ai.greycos.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
                        import ai.greycos.solver.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore;
                        import ai.greycos.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
                        import ai.greycos.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
                        import ai.greycos.solver.core.api.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScore;
                        import ai.greycos.solver.core.api.score.buildin.bendable.BendableScore;
                        import ai.greycos.solver.core.api.score.buildin.bendablelong.BendableLongScore;
                        import ai.greycos.solver.core.api.score.buildin.bendablebigdecimal.BendableBigDecimalScore;

                        public class Test {
                                ScoreDirector scoreDirector;
                                SimpleScore simpleScore;
                                SimpleLongScore simpleLongScore;
                                SimpleBigDecimalScore simpleBigDecimalScore;
                                HardSoftScore hardSoftScore;
                                HardSoftLongScore hardSoftLongScore;
                                HardSoftBigDecimalScore hardSoftBigDecimalScore;
                                HardMediumSoftScore hardMediumSoftScore;
                                HardMediumSoftLongScore hardMediumSoftLongScore;
                                HardMediumSoftBigDecimalScore hardMediumSoftBigDecimalScore;
                                BendableScore bendableScore;
                                BendableLongScore bendableLongScore;
                                BendableBigDecimalScore bendableBigDecimalScore;
                        }""",
            """
                        package greycos;

                        import ai.greycos.solver.core.impl.score.director.ScoreDirector;
                        import ai.greycos.solver.core.api.score.BendableBigDecimalScore;
                        import ai.greycos.solver.core.api.score.BendableScore;
                        import ai.greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
                        import ai.greycos.solver.core.api.score.HardMediumSoftScore;
                        import ai.greycos.solver.core.api.score.HardSoftBigDecimalScore;
                        import ai.greycos.solver.core.api.score.HardSoftScore;
                        import ai.greycos.solver.core.api.score.SimpleBigDecimalScore;
                        import ai.greycos.solver.core.api.score.SimpleScore;

                        public class Test {
                                ScoreDirector scoreDirector;
                                SimpleScore simpleScore;
                                SimpleScore simpleLongScore;
                                SimpleBigDecimalScore simpleBigDecimalScore;
                                HardSoftScore hardSoftScore;
                                HardSoftScore hardSoftLongScore;
                                HardSoftBigDecimalScore hardSoftBigDecimalScore;
                                HardMediumSoftScore hardMediumSoftScore;
                                HardMediumSoftScore hardMediumSoftLongScore;
                                HardMediumSoftBigDecimalScore hardMediumSoftBigDecimalScore;
                                BendableScore bendableScore;
                                BendableScore bendableLongScore;
                                BendableBigDecimalScore bendableBigDecimalScore;
                        }"""));
  }

  @Test
  void migrateValueRange() {
    rewriteRun(
        java(
            """
                        package greycos;

                        import ai.greycos.solver.core.api.domain.valuerange.CountableValueRange;
                        import ai.greycos.solver.core.impl.domain.valuerange.buildin.composite.CompositeCountableValueRange;
                        import ai.greycos.solver.core.impl.domain.valuerange.buildin.composite.NullAllowingCountableValueRange;

                        public class Test {
                                CountableValueRange valueRange;
                                CompositeCountableValueRange valueRange2;
                                NullAllowingCountableValueRange valueRange3;
                        }""",
            """
                        package greycos;

                        import ai.greycos.solver.core.api.domain.valuerange.ValueRange;
                        import ai.greycos.solver.core.impl.domain.valuerange.CompositeValueRange;
                        import ai.greycos.solver.core.impl.domain.valuerange.NullAllowingValueRange;

                        public class Test {
                                ValueRange valueRange;
                                CompositeValueRange valueRange2;
                                NullAllowingValueRange valueRange3;
                        }"""));
  }
}
