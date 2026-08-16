package greycos.solver.migration.v2;

import static org.openrewrite.java.Assertions.java;

import java.util.List;

import greycos.solver.migration.NoWildCardImportStyle;

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
                    "package greycos.solver.core.api.domain.lookup; public class PlanningId {}",
                    "package greycos.solver.core.api.score.director; public class ScoreDirector {}",
                    "package greycos.solver.core.api.score.buildin.simple; public class SimpleScore {}",
                    "package greycos.solver.core.api.score.buildin.simplelong; public class SimpleLongScore {}",
                    "package greycos.solver.core.api.score.buildin.simplebigdecimal; public class SimpleBigDecimalScore {}",
                    "package greycos.solver.core.api.score.buildin.hardsoft; public class HardSoftScore {}",
                    "package greycos.solver.core.api.score.buildin.hardsoftlong; public class HardSoftLongScore {}",
                    "package greycos.solver.core.api.score.buildin.hardsoftbigdecimal; public class HardSoftBigDecimalScore {}",
                    "package greycos.solver.core.api.score.buildin.hardmediumsoft; public class HardMediumSoftScore {}",
                    "package greycos.solver.core.api.score.buildin.hardmediumsoftlong; public class HardMediumSoftLongScore {}",
                    "package greycos.solver.core.api.score.buildin.hardmediumsoftbigdecimal; public class HardMediumSoftBigDecimalScore {}",
                    "package greycos.solver.core.api.score.buildin.bendable; public class BendableScore {}",
                    "package greycos.solver.core.api.score.buildin.bendablelong; public class BendableLongScore {}",
                    "package greycos.solver.core.api.score.buildin.bendablebigdecimal; public class BendableBigDecimalScore {}",
                    "package greycos.solver.core.api.score.constraint; public class ConstraintRef {}",
                    "package greycos.solver.core.api.solver; public class ProblemFactChange {}",
                    "package greycos.solver.core.api.domain.valuerange; public class CountableValueRange {}",
                    "package greycos.solver.core.api.domain.valuerange; public class ValueRange {}",
                    "package greycos.solver.core.impl.domain.valuerange.buildin.composite; public class CompositeCountableValueRange {}",
                    "package greycos.solver.core.impl.domain.valuerange.buildin.composite; public class NullAllowingCountableValueRange {}",
                    "package greycos.solver.core.impl.heuristic.move; public interface Move {}",
                    "package greycos.solver.core.impl.heuristic.move; public interface AbstractMove {}",
                    "package greycos.solver.core.impl.heuristic.move; public interface NoChangeMove {}",
                    "package greycos.solver.core.impl.heuristic.move; public interface CompositeMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt; public interface KOptListMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt; public interface TwoOptListMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin; public interface ListRuinRecreateMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface ListAssignMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface ListChangeMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface ListSwapMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface ListUnassignMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface SubListChangeMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface SubListSwapMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic.list; public interface SubListUnassignMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic; public interface ChangeMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic; public interface PillarChangeMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic; public interface PillarSwapMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic; public interface RuinRecreateMove {}",
                    "package greycos.solver.core.impl.heuristic.selector.move.generic; public interface SwapMove {}"));
  }

  @Test
  void migrate() {
    rewriteRun(
        java(
            """
                        package greycos;

                        import greycos.solver.core.api.domain.lookup.PlanningId;
                        import greycos.solver.core.api.solver.ProblemFactChange;

                        public class Test {
                                @PlanningId
                                SimpleScore simpleScore;
                                ProblemFactChange  problemFactChange;
                        }""",
            """
                        package greycos;

                        import greycos.solver.core.api.cotwin.common.PlanningId;
                        import greycos.solver.core.api.solver.change.ProblemChange;

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

                        import greycos.solver.core.api.score.director.ScoreDirector;
                        import greycos.solver.core.api.score.buildin.simple.SimpleScore;
                        import greycos.solver.core.api.score.buildin.simplelong.SimpleLongScore;
                        import greycos.solver.core.api.score.buildin.simplebigdecimal.SimpleBigDecimalScore;
                        import greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;
                        import greycos.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
                        import greycos.solver.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore;
                        import greycos.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
                        import greycos.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
                        import greycos.solver.core.api.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScore;
                        import greycos.solver.core.api.score.buildin.bendable.BendableScore;
                        import greycos.solver.core.api.score.buildin.bendablelong.BendableLongScore;
                        import greycos.solver.core.api.score.buildin.bendablebigdecimal.BendableBigDecimalScore;

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

                        import greycos.solver.core.impl.score.director.ScoreDirector;
                        import greycos.solver.core.api.score.BendableBigDecimalScore;
                        import greycos.solver.core.api.score.BendableScore;
                        import greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
                        import greycos.solver.core.api.score.HardMediumSoftScore;
                        import greycos.solver.core.api.score.HardSoftBigDecimalScore;
                        import greycos.solver.core.api.score.HardSoftScore;
                        import greycos.solver.core.api.score.SimpleBigDecimalScore;
                        import greycos.solver.core.api.score.SimpleScore;

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
  void migrateConstraintRef() {
    rewriteRun(
        java(
            """
                        package greycos;

                        import greycos.solver.core.api.score.constraint.ConstraintRef;

                        public class Test {
                                ConstraintRef constraintRef;
                        }""",
            """
                        package greycos;

                        import greycos.solver.core.api.score.stream.ConstraintRef;

                        public class Test {
                                ConstraintRef constraintRef;
                        }"""));
  }

  @Test
  void migrateValueRange() {
    rewriteRun(
        java(
            """
                        package greycos;

                        import greycos.solver.core.api.domain.valuerange.CountableValueRange;
                        import greycos.solver.core.impl.domain.valuerange.buildin.composite.CompositeCountableValueRange;
                        import greycos.solver.core.impl.domain.valuerange.buildin.composite.NullAllowingCountableValueRange;

                        public class Test {
                                CountableValueRange valueRange;
                                CompositeCountableValueRange valueRange2;
                                NullAllowingCountableValueRange valueRange3;
                        }""",
            """
                        package greycos;

                        import greycos.solver.core.api.cotwin.valuerange.ValueRange;
                        import greycos.solver.core.impl.cotwin.valuerange.CompositeValueRange;
                        import greycos.solver.core.impl.cotwin.valuerange.NullAllowingValueRange;

                        public class Test {
                                ValueRange valueRange;
                                CompositeValueRange valueRange2;
                                NullAllowingValueRange valueRange3;
                        }"""));
  }
}
