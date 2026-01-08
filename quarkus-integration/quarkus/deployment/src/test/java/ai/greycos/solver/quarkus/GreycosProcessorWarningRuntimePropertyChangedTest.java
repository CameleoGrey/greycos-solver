package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.logging.Level;

import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusConstraintProvider;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusEntity;
import ai.greycos.solver.quarkus.testdomain.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorWarningRuntimePropertyChangedTest {
  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.move-thread-count", "1")
          .overrideConfigKey("quarkus.greycos.solver.termination.spent-limit", "1s")
          // We overwrite the value at runtime
          .overrideRuntimeConfigKey("quarkus.greycos.solver.move-thread-count", "2")
          .overrideRuntimeConfigKey("quarkus.greycos.solver.termination.spent-limit", "2s")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusConstraintProvider.class))
          // Make sure Quarkus does not produce a warning for overwriting a build time value at
          // runtime
          .setLogRecordPredicate(
              logRecord ->
                  logRecord.getLoggerName().startsWith("io.quarkus")
                      && logRecord.getLevel().intValue() >= Level.WARNING.intValue());

  @Test
  void solverProperties() {
    config.assertLogRecords(
        logRecords -> {
          assertEquals(0, logRecords.size(), "expected no warnings to be generated");
        });
  }
}
