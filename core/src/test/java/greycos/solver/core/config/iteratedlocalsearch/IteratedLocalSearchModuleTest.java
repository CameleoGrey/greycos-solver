package greycos.solver.core.config.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.module.ModuleFinder;
import java.nio.file.Path;

import greycos.solver.core.impl.io.jaxb.JaxbMoveSelectorConfigAdapter;

import org.junit.jupiter.api.Test;

/** Checks the production JPMS descriptor even when Surefire runs tests on the class path. */
class IteratedLocalSearchModuleTest {

  @Test
  void publicConfigurationIsExportedAndJaxbCanAccessBothRepresentations() throws Exception {
    var coreLocation =
        Path.of(
            IteratedLocalSearchPhaseConfig.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    var descriptor =
        ModuleFinder.of(coreLocation).find("greycos.solver.core").orElseThrow().descriptor();
    var configPackage = IteratedLocalSearchPhaseConfig.class.getPackageName();
    assertThat(descriptor.exports())
        .anySatisfy(
            export -> {
              assertThat(export.source()).isEqualTo(configPackage);
              assertThat(export.isQualified()).isFalse();
            });
    assertThat(descriptor.opens())
        .anySatisfy(
            open -> {
              assertThat(open.source()).isEqualTo(configPackage);
              assertThat(open.targets()).contains("jakarta.xml.bind", "org.glassfish.jaxb.runtime");
            });
    assertThat(descriptor.opens())
        .anySatisfy(
            open -> {
              assertThat(open.source())
                  .isEqualTo(JaxbMoveSelectorConfigAdapter.class.getPackageName());
              assertThat(open.isQualified()).isFalse();
            });
    assertThat(descriptor.exports())
        .anySatisfy(
            export -> {
              assertThat(export.source()).isEqualTo("greycos.solver.core.impl.iteratedlocalsearch");
              assertThat(export.targets()).contains("greycos.solver.benchmark");
            });
  }
}
