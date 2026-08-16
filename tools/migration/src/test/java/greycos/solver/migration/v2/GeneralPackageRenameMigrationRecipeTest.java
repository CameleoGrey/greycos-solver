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
class GeneralPackageRenameMigrationRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipes(new GeneralPackageRenameMigrationRecipe())
        .typeValidationOptions(TypeValidation.builder().allowMissingType(ignore -> true).build())
        .parser(
            JavaParser.fromJavaVersion()
                .styles(List.of(new NoWildCardImportStyle()))
                // We must add all old classes as stubs to the JavaTemplate
                .dependsOn(
                    // Persistence API
                    "package greycos.solver.persistence.common.api.domain.solution; public class SolutionFileIO {}",
                    "package greycos.solver.jpa.api.score.buildin.bendablebigdecimal; public class BendableBigDecimalScoreConverter {}",
                    "package greycos.solver.jpa.api.score.buildin.bendable; public class BendableScoreConverter {}",
                    "package greycos.solver.jpa.api.score.buildin.hardmediumsoftbigdecimal; public class HardMediumSoftBigDecimalScoreConverter {}",
                    "package greycos.solver.jpa.api.score.buildin.hardmediumsoft; public class HardMediumSoftScoreConverter {}",
                    "package greycos.solver.jpa.api.score.buildin.hardsoftbigdecimal; public class HardSoftBigDecimalScoreConverter {}",
                    "package greycos.solver.jpa.api.score.buildin.hardsoft; public class HardSoftScoreConverter {}",
                    "package greycos.solver.jpa.api.score.buildin.simplebigdecimal; public class SimpleBigDecimalScoreConverter {}",
                    "package greycos.solver.jpa.api.score.buildin.simple; public class SimpleScoreConverter {}",
                    // Jackson API
                    "package greycos.solver.jackson.api.score.buildin.bendablebigdecimal; public class BendableBigDecimalScoreJacksonDeserializer {}",
                    "package greycos.solver.jackson.api.score.buildin.bendable; public class BendableScoreJacksonDeserializer {}",
                    "package greycos.solver.jackson.api.score.buildin.hardmediumsoftbigdecimal; public class HardMediumSoftBigDecimalScoreJacksonDeserializer {}",
                    "package greycos.solver.jackson.api.score.buildin.hardmediumsoft; public class HardMediumSoftScoreJacksonDeserializer {}",
                    "package greycos.solver.jackson.api.score.buildin.hardsoftbigdecimal; public class HardSoftBigDecimalScoreJacksonDeserializer {}",
                    "package greycos.solver.jackson.api.score.buildin.hardsoft; public class HardSoftScoreJacksonDeserializer {}",
                    "package greycos.solver.jackson.api.score.buildin.simplebigdecimal; public class SimpleBigDecimalScoreJacksonDeserializer {}",
                    "package greycos.solver.jackson.api.score.buildin.simple; public class SimpleScoreJacksonDeserializer {}",
                    // JAXB API
                    "package greycos.solver.jaxb.api.score.buildin.bendablebigdecimal; public class BendableBigDecimalScoreJaxbAdapter {}",
                    "package greycos.solver.jaxb.api.score.buildin.bendable; public class BendableScoreJaxbAdapter {}",
                    "package greycos.solver.jaxb.api.score.buildin.hardmediumsoftbigdecimal; public class HardMediumSoftBigDecimalScoreJaxbAdapter {}",
                    "package greycos.solver.jaxb.api.score.buildin.hardmediumsoft; public class HardMediumSoftScoreJaxbAdapter {}",
                    "package greycos.solver.jaxb.api.score.buildin.hardsoftbigdecimal; public class HardSoftBigDecimalScoreJaxbAdapter {}",
                    "package greycos.solver.jaxb.api.score.buildin.hardsoft; public class HardSoftScoreJaxbAdapter {}",
                    "package greycos.solver.jaxb.api.score.buildin.simplebigdecimal; public class SimpleBigDecimalScoreJaxbAdapter {}",
                    "package greycos.solver.jaxb.api.score.buildin.simple; public class SimpleScoreJaxbAdapter {}",
                    // Jackson API
                    "package greycos.solver.quarkus.jackson.score.buildin.bendablebigdecimal; public class BendableBigDecimalScoreJacksonDeserializer {}",
                    "package greycos.solver.quarkus.jackson.score.buildin.bendable; public class BendableScoreJacksonDeserializer {}",
                    "package greycos.solver.quarkus.jackson.score.buildin.hardmediumsoftbigdecimal; public class HardMediumSoftBigDecimalScoreJacksonDeserializer {}",
                    "package greycos.solver.quarkus.jackson.score.buildin.hardmediumsoft; public class HardMediumSoftScoreJacksonDeserializer {}",
                    "package greycos.solver.quarkus.jackson.score.buildin.hardsoftbigdecimal; public class HardSoftBigDecimalScoreJacksonDeserializer {}",
                    "package greycos.solver.quarkus.jackson.score.buildin.hardsoft; public class HardSoftScoreJacksonDeserializer {}",
                    "package greycos.solver.quarkus.jackson.score.buildin.simplebigdecimal; public class SimpleBigDecimalScoreJacksonDeserializer {}",
                    "package greycos.solver.quarkus.jackson.score.buildin.simple; public class SimpleScoreJacksonDeserializer {}",
                    // Value Range API
                    "package greycos.solver.core.impl.domain.valuerange.buildin.bigdecimal; public class BigDecimalValueRange {}",
                    "package greycos.solver.core.impl.domain.valuerange.buildin.biginteger; public class BigIntegerValueRange {}",
                    "package greycos.solver.core.impl.domain.valuerange.buildin.primboolean; public class BooleanValueRange {}",
                    "package greycos.solver.core.impl.domain.valuerange.buildin.primint; public class IntValueRange {}",
                    "package greycos.solver.core.impl.domain.valuerange.buildin.collection; public class ListValueRange {}",
                    "package greycos.solver.core.impl.domain.valuerange.buildin.primlong; public class LongValueRange {}",
                    "package greycos.solver.core.impl.domain.valuerange.buildin.temporal; public class TemporalValueRange {}",
                    "package greycos.solver.core.impl.domain.valuerange.buildin; public class EmptyValueRange {}"));
  }

  @Test
  void migratePersistence() {
    rewriteRun(
        java(
            """
                        package greycos;

                        import greycos.solver.persistence.common.api.domain.solution.SolutionFileIO;
                        import greycos.solver.jpa.api.score.buildin.bendablebigdecimal.BendableBigDecimalScoreConverter;
                        import greycos.solver.jpa.api.score.buildin.bendable.BendableScoreConverter;
                        import greycos.solver.jpa.api.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScoreConverter;
                        import greycos.solver.jpa.api.score.buildin.hardmediumsoft.HardMediumSoftScoreConverter;
                        import greycos.solver.jpa.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScoreConverter;
                        import greycos.solver.jpa.api.score.buildin.hardsoft.HardSoftScoreConverter;
                        import greycos.solver.jpa.api.score.buildin.simplebigdecimal.SimpleBigDecimalScoreConverter;
                        import greycos.solver.jpa.api.score.buildin.simple.SimpleScoreConverter;

                        public class Test {
                                SolutionFileIO solutionFileIO;
                                BendableBigDecimalScoreConverter bendableBigDecimalScoreConverter;
                                BendableScoreConverter bendableScoreConverter;
                                HardMediumSoftBigDecimalScoreConverter hardMediumSoftBigDecimalScoreConverter;
                                HardMediumSoftScoreConverter hardMediumSoftScoreConverter;
                                HardSoftBigDecimalScoreConverter hardSoftBigDecimalScoreConverter;
                                HardSoftScoreConverter hardSoftScoreConverter;
                                SimpleBigDecimalScoreConverter simpleBigDecimalScoreConverter;
                                SimpleScoreConverter simpleScoreConverter;
                        }""",
            """
                        package greycos;

                        import greycos.solver.core.api.cotwin.solution.SolutionFileIO;
                        import greycos.solver.jpa.api.score.BendableBigDecimalScoreConverter;
                        import greycos.solver.jpa.api.score.BendableScoreConverter;
                        import greycos.solver.jpa.api.score.HardMediumSoftBigDecimalScoreConverter;
                        import greycos.solver.jpa.api.score.HardMediumSoftScoreConverter;
                        import greycos.solver.jpa.api.score.HardSoftBigDecimalScoreConverter;
                        import greycos.solver.jpa.api.score.HardSoftScoreConverter;
                        import greycos.solver.jpa.api.score.SimpleBigDecimalScoreConverter;
                        import greycos.solver.jpa.api.score.SimpleScoreConverter;

                        public class Test {
                                SolutionFileIO solutionFileIO;
                                BendableBigDecimalScoreConverter bendableBigDecimalScoreConverter;
                                BendableScoreConverter bendableScoreConverter;
                                HardMediumSoftBigDecimalScoreConverter hardMediumSoftBigDecimalScoreConverter;
                                HardMediumSoftScoreConverter hardMediumSoftScoreConverter;
                                HardSoftBigDecimalScoreConverter hardSoftBigDecimalScoreConverter;
                                HardSoftScoreConverter hardSoftScoreConverter;
                                SimpleBigDecimalScoreConverter simpleBigDecimalScoreConverter;
                                SimpleScoreConverter simpleScoreConverter;
                        }"""));
  }

  @Test
  void migrateJackson() {
    rewriteRun(
        java(
            """
                        package greycos;

                        import greycos.solver.jackson.api.score.buildin.bendablebigdecimal.BendableBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.buildin.bendable.BendableScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.buildin.hardmediumsoft.HardMediumSoftScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.buildin.hardsoft.HardSoftScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.buildin.simplebigdecimal.SimpleBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.buildin.simple.SimpleScoreJacksonDeserializer;

                        public class Test {
                                BendableBigDecimalScoreJacksonDeserializer bendableBigDecimalScoreJacksonDeserializer;
                                BendableScoreJacksonDeserializer bendableScoreJacksonDeserializer;
                                HardMediumSoftBigDecimalScoreJacksonDeserializer hardMediumSoftBigDecimalScoreJacksonDeserializer;
                                HardMediumSoftScoreJacksonDeserializer hardMediumSoftScoreJacksonDeserializer;
                                HardSoftBigDecimalScoreJacksonDeserializer hardSoftBigDecimalScoreJacksonDeserializer;
                                HardSoftScoreJacksonDeserializer hardSoftScoreJacksonDeserializer;
                                SimpleBigDecimalScoreJacksonDeserializer simpleBigDecimalScoreJacksonDeserializer;
                                SimpleScoreJacksonDeserializer simpleScoreJacksonDeserializer;
                        }""",
            """
                        package greycos;

                        import greycos.solver.jackson.api.score.BendableBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.BendableScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.HardMediumSoftBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.HardMediumSoftScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.HardSoftBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.HardSoftScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.SimpleBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.jackson.api.score.SimpleScoreJacksonDeserializer;

                        public class Test {
                                BendableBigDecimalScoreJacksonDeserializer bendableBigDecimalScoreJacksonDeserializer;
                                BendableScoreJacksonDeserializer bendableScoreJacksonDeserializer;
                                HardMediumSoftBigDecimalScoreJacksonDeserializer hardMediumSoftBigDecimalScoreJacksonDeserializer;
                                HardMediumSoftScoreJacksonDeserializer hardMediumSoftScoreJacksonDeserializer;
                                HardSoftBigDecimalScoreJacksonDeserializer hardSoftBigDecimalScoreJacksonDeserializer;
                                HardSoftScoreJacksonDeserializer hardSoftScoreJacksonDeserializer;
                                SimpleBigDecimalScoreJacksonDeserializer simpleBigDecimalScoreJacksonDeserializer;
                                SimpleScoreJacksonDeserializer simpleScoreJacksonDeserializer;
                        }"""));
  }

  @Test
  void migrateJaxb() {
    rewriteRun(
        java(
            """
                        package greycos;

                        import greycos.solver.jaxb.api.score.buildin.bendablebigdecimal.BendableBigDecimalScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.buildin.bendable.BendableScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.buildin.hardmediumsoft.HardMediumSoftScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.buildin.hardsoft.HardSoftScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.buildin.simplebigdecimal.SimpleBigDecimalScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.buildin.simple.SimpleScoreJaxbAdapter;

                        public class Test {
                                BendableBigDecimalScoreJaxbAdapter bendableBigDecimalScoreJaxbAdapter;
                                BendableScoreJaxbAdapter bendableScoreJaxbAdapter;
                                HardMediumSoftBigDecimalScoreJaxbAdapter hardMediumSoftBigDecimalScoreJaxbAdapter;
                                HardMediumSoftScoreJaxbAdapter hardMediumSoftScoreJaxbAdapter;
                                HardSoftBigDecimalScoreJaxbAdapter hardSoftBigDecimalScoreJaxbAdapter;
                                HardSoftScoreJaxbAdapter hardSoftScoreJaxbAdapter;
                                SimpleBigDecimalScoreJaxbAdapter simpleBigDecimalScoreJaxbAdapter;
                                SimpleScoreJaxbAdapter simpleScoreJaxbAdapter;
                        }""",
            """
                        package greycos;

                        import greycos.solver.jaxb.api.score.BendableBigDecimalScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.BendableScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.HardMediumSoftBigDecimalScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.HardMediumSoftScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.HardSoftBigDecimalScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.HardSoftScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.SimpleBigDecimalScoreJaxbAdapter;
                        import greycos.solver.jaxb.api.score.SimpleScoreJaxbAdapter;

                        public class Test {
                                BendableBigDecimalScoreJaxbAdapter bendableBigDecimalScoreJaxbAdapter;
                                BendableScoreJaxbAdapter bendableScoreJaxbAdapter;
                                HardMediumSoftBigDecimalScoreJaxbAdapter hardMediumSoftBigDecimalScoreJaxbAdapter;
                                HardMediumSoftScoreJaxbAdapter hardMediumSoftScoreJaxbAdapter;
                                HardSoftBigDecimalScoreJaxbAdapter hardSoftBigDecimalScoreJaxbAdapter;
                                HardSoftScoreJaxbAdapter hardSoftScoreJaxbAdapter;
                                SimpleBigDecimalScoreJaxbAdapter simpleBigDecimalScoreJaxbAdapter;
                                SimpleScoreJaxbAdapter simpleScoreJaxbAdapter;
                        }"""));
  }

  @Test
  void migrateQuarkusJackson() {
    rewriteRun(
        java(
            """
                        package greycos;

                        import greycos.solver.quarkus.jackson.score.buildin.bendablebigdecimal.BendableBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.buildin.bendable.BendableScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.buildin.hardmediumsoft.HardMediumSoftScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.buildin.hardsoft.HardSoftScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.buildin.simplebigdecimal.SimpleBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.buildin.simple.SimpleScoreJacksonDeserializer;

                        public class Test {
                                BendableBigDecimalScoreJacksonDeserializer bendableBigDecimalScoreJacksonDeserializer;
                                BendableScoreJacksonDeserializer bendableScoreJacksonDeserializer;
                                HardMediumSoftBigDecimalScoreJacksonDeserializer hardMediumSoftBigDecimalScoreJacksonDeserializer;
                                HardMediumSoftScoreJacksonDeserializer hardMediumSoftScoreJacksonDeserializer;
                                HardSoftBigDecimalScoreJacksonDeserializer hardSoftBigDecimalScoreJacksonDeserializer;
                                HardSoftScoreJacksonDeserializer hardSoftScoreJacksonDeserializer;
                                SimpleBigDecimalScoreJacksonDeserializer simpleBigDecimalScoreJacksonDeserializer;
                                SimpleScoreJacksonDeserializer simpleScoreJacksonDeserializer;
                        }""",
            """
                        package greycos;

                        import greycos.solver.quarkus.jackson.score.BendableBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.BendableScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.HardMediumSoftBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.HardMediumSoftScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.HardSoftBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.HardSoftScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.SimpleBigDecimalScoreJacksonDeserializer;
                        import greycos.solver.quarkus.jackson.score.SimpleScoreJacksonDeserializer;

                        public class Test {
                                BendableBigDecimalScoreJacksonDeserializer bendableBigDecimalScoreJacksonDeserializer;
                                BendableScoreJacksonDeserializer bendableScoreJacksonDeserializer;
                                HardMediumSoftBigDecimalScoreJacksonDeserializer hardMediumSoftBigDecimalScoreJacksonDeserializer;
                                HardMediumSoftScoreJacksonDeserializer hardMediumSoftScoreJacksonDeserializer;
                                HardSoftBigDecimalScoreJacksonDeserializer hardSoftBigDecimalScoreJacksonDeserializer;
                                HardSoftScoreJacksonDeserializer hardSoftScoreJacksonDeserializer;
                                SimpleBigDecimalScoreJacksonDeserializer simpleBigDecimalScoreJacksonDeserializer;
                                SimpleScoreJacksonDeserializer simpleScoreJacksonDeserializer;
                        }"""));
  }

  @Test
  void migrateValueRange() {
    rewriteRun(
        java(
            """
                        package greycos;

                        import greycos.solver.core.impl.domain.valuerange.buildin.bigdecimal.BigDecimalValueRange;
                        import greycos.solver.core.impl.domain.valuerange.buildin.biginteger.BigIntegerValueRange;
                        import greycos.solver.core.impl.domain.valuerange.buildin.primboolean.BooleanValueRange;
                        import greycos.solver.core.impl.domain.valuerange.buildin.primint.IntValueRange;
                        import greycos.solver.core.impl.domain.valuerange.buildin.collection.ListValueRange;
                        import greycos.solver.core.impl.domain.valuerange.buildin.primlong.LongValueRange;
                        import greycos.solver.core.impl.domain.valuerange.buildin.temporal.TemporalValueRange;
                        import greycos.solver.core.impl.domain.valuerange.buildin.EmptyValueRange;

                        public class Test {
                                BigDecimalValueRange bigDecimalValueRange;
                                BigIntegerValueRange bigIntegerValueRange;
                                BooleanValueRange booleanValueRange;
                                IntValueRange intValueRange;
                                ListValueRange listValueRange;
                                LongValueRange longValueRange;
                                TemporalValueRange temporalValueRange;
                                EmptyValueRange emptyValueRange;
                        }""",
            """
                        package greycos;

                        import greycos.solver.core.impl.cotwin.valuerange.BigDecimalValueRange;
                        import greycos.solver.core.impl.cotwin.valuerange.BigIntegerValueRange;
                        import greycos.solver.core.impl.cotwin.valuerange.BooleanValueRange;
                        import greycos.solver.core.impl.cotwin.valuerange.IntValueRange;
                        import greycos.solver.core.impl.cotwin.valuerange.ListValueRange;
                        import greycos.solver.core.impl.cotwin.valuerange.LongValueRange;
                        import greycos.solver.core.impl.cotwin.valuerange.TemporalValueRange;
                        import greycos.solver.core.impl.cotwin.valuerange.EmptyValueRange;

                        public class Test {
                                BigDecimalValueRange bigDecimalValueRange;
                                BigIntegerValueRange bigIntegerValueRange;
                                BooleanValueRange booleanValueRange;
                                IntValueRange intValueRange;
                                ListValueRange listValueRange;
                                LongValueRange longValueRange;
                                TemporalValueRange temporalValueRange;
                                EmptyValueRange emptyValueRange;
                        }"""));
  }
}
