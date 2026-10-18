package greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import greycos.solver.core.config.AbstractConfig;
import greycos.solver.core.config.alns.AlnsMoveThreadingMode;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchStepLoggingMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.SolverManagerConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import tools.jackson.databind.json.JsonMapper;

class GreyCOSSolverNativeReflectionMetadataTest {

  @Test
  void configurationClassesAndTheirEnumFieldsAreRegistered() throws Exception {
    var registrations = readReflectionRegistrations();
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(AbstractConfig.class));
    // The manager has its own configuration, outside the XML model bound by SolverConfigIO.
    scanner.addExcludeFilter(new AssignableTypeFilter(SolverManagerConfig.class));
    var configTypes = new HashSet<Class<?>>();
    for (var candidate : scanner.findCandidateComponents("greycos.solver.core.config")) {
      for (var type = Class.forName(candidate.getBeanClassName());
          AbstractConfig.class.isAssignableFrom(type);
          type = type.getSuperclass()) {
        configTypes.add(type);
      }
    }
    assertThat(configTypes)
        .contains(SolverConfig.class, IslandModelPhaseConfig.class)
        .doesNotContain(SolverManagerConfig.class);

    var enums = new HashSet<Class<?>>();
    for (var type : configTypes) {
      var registration = registrations.get(type.getName());
      assertThat(registration)
          .as("Native JAXB reflection metadata for %s", type.getName())
          .isNotNull()
          .containsEntry("allDeclaredFields", true)
          .containsEntry("queryAllDeclaredMethods", true);
      if (!Modifier.isAbstract(type.getModifiers())) {
        assertThat((List<?>) registration.get("methods"))
            .as("Native JAXB no-argument constructor for %s", type.getName())
            .anySatisfy(
                method ->
                    assertThat(method)
                        .isEqualTo(Map.of("name", "<init>", "parameterTypes", List.of())));
      }
      Arrays.stream(type.getDeclaredFields())
          .filter(field -> !Modifier.isStatic(field.getModifiers()))
          .flatMap(field -> enumTypes(field.getGenericType()))
          .forEach(enums::add);
    }
    assertThat(enums).contains(AlnsMoveThreadingMode.class, LocalSearchStepLoggingMode.class);
    // JAXB inspects every bound enum while creating the context, even when its setting is absent.
    for (var type : enums) {
      assertThat(registrations.get(type.getName()))
          .as("Native JAXB enum constant fields for %s", type.getName())
          .isNotNull()
          .containsEntry("allDeclaredFields", true);
    }
  }

  @Test
  void aotRoundTripPreservesIslandPhasesAndNewEnumSettings() {
    var config =
        new SolverConfig()
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withPhaseConfigList(
                        List.of(
                            new AlnsPhaseConfig()
                                .withMoveThreadingMode(AlnsMoveThreadingMode.REPAIR_ATTEMPTS),
                            new LocalSearchPhaseConfig()
                                .withStepLoggingMode(
                                    LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED))));
    var writer = new StringWriter();
    new SolverConfigIO().write(config, writer);

    var restored = new GreyCOSSolverAotFactory().solverConfigSupplier(writer.toString());
    var island = (IslandModelPhaseConfig) restored.getPhaseConfigList().getFirst();
    assertThat(island.getIslandCount()).isEqualTo(2);
    assertThat(island.getPhaseConfigList()).hasSize(2);
    assertThat(((AlnsPhaseConfig) island.getPhaseConfigList().getFirst()).getMoveThreadingMode())
        .isEqualTo(AlnsMoveThreadingMode.REPAIR_ATTEMPTS);
    assertThat(
            ((LocalSearchPhaseConfig) island.getPhaseConfigList().getLast()).getStepLoggingMode())
        .isEqualTo(LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED);
  }

  private static Stream<Class<?>> enumTypes(Type type) {
    if (type instanceof Class<?> clazz && clazz.isEnum()) {
      return Stream.of(clazz);
    }
    if (type instanceof ParameterizedType parameterizedType) {
      return Arrays.stream(parameterizedType.getActualTypeArguments())
          .flatMap(GreyCOSSolverNativeReflectionMetadataTest::enumTypes);
    }
    return Stream.empty();
  }

  private Map<String, Map<String, Object>> readReflectionRegistrations() throws Exception {
    try (var resource =
        getClass()
            .getResourceAsStream(
                "/META-INF/native-image/greycos.solver/greycos-solver-spring-boot-autoconfigure/reflect-config.json")) {
      assertThat(resource).isNotNull();
      var mapper = JsonMapper.builder().build();
      List<Map<String, Object>> registrations =
          mapper.readValue(
              resource, mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
      var byName = new HashMap<String, Map<String, Object>>();
      registrations.forEach(
          registration -> byName.put((String) registration.get("name"), registration));
      return byName;
    }
  }
}
