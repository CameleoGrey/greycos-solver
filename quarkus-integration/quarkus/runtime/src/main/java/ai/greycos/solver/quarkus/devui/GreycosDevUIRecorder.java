package ai.greycos.solver.quarkus.devui;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import ai.greycos.solver.core.api.domain.solution.cloner.SolutionCloner;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.impl.domain.common.accessor.MemberAccessor;
import ai.greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import ai.greycos.solver.quarkus.GreyCOSRecorder;
import ai.greycos.solver.quarkus.config.GreyCOSRuntimeConfig;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class GreyCOSDevUIRecorder {
  final RuntimeValue<GreyCOSRuntimeConfig> greycosRuntimeConfig;

  public GreyCOSDevUIRecorder(final RuntimeValue<GreyCOSRuntimeConfig> greycosRuntimeConfig) {
    this.greycosRuntimeConfig = greycosRuntimeConfig;
  }

  public <Solution_> Supplier<DevUISolverConfig> solverConfigSupplier(
      Map<String, SolverConfig> allSolverConfig,
      Map<String, RuntimeValue<MemberAccessor>> generatedGizmoMemberAccessorMap,
      Map<String, RuntimeValue<SolutionCloner<Solution_>>> generatedGizmoSolutionClonerMap) {
    return () -> {
      DevUISolverConfig uiSolverConfig = new DevUISolverConfig();
      allSolverConfig.forEach(
          (solverName, solverConfig) -> {
            updateSolverConfigWithRuntimeProperties(solverName, solverConfig);
            Map<String, MemberAccessor> memberAccessorMap = new HashMap<>();
            Map<String, SolutionCloner> solutionClonerMap = new HashMap<>();
            generatedGizmoMemberAccessorMap.forEach(
                (className, runtimeValue) ->
                    memberAccessorMap.put(className, runtimeValue.getValue()));
            generatedGizmoSolutionClonerMap.forEach(
                (className, runtimeValue) ->
                    solutionClonerMap.put(className, runtimeValue.getValue()));

            solverConfig.setGizmoMemberAccessorMap(memberAccessorMap);
            solverConfig.setGizmoSolutionClonerMap(solutionClonerMap);

            StringWriter effectiveSolverConfigWriter = new StringWriter();
            SolverConfigIO solverConfigIO = new SolverConfigIO();
            solverConfigIO.write(solverConfig, effectiveSolverConfigWriter);

            uiSolverConfig.setSolverConfigFile(solverName, effectiveSolverConfigWriter.toString());
            uiSolverConfig.setFactory(solverName, SolverFactory.create(solverConfig));
          });
      return uiSolverConfig;
    };
  }

  private void updateSolverConfigWithRuntimeProperties(
      String solverName, SolverConfig solverConfig) {
    GreyCOSRecorder.updateSolverConfigWithRuntimeProperties(
        solverConfig,
        greycosRuntimeConfig.getValue().getSolverRuntimeConfig(solverName).orElse(null));
  }
}
