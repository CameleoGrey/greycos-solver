package ai.greycos.solver.spring.boot.autoconfigure;

import java.io.StringReader;

import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.config.solver.SolverManagerConfig;
import ai.greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import ai.greycos.solver.spring.boot.autoconfigure.config.GreycosProperties;
import ai.greycos.solver.spring.boot.autoconfigure.config.SolverManagerProperties;

import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

public class GreycosSolverAotFactory implements EnvironmentAware {
  private GreycosProperties greycosProperties;

  @Override
  public void setEnvironment(Environment environment) {
    // We need the environment to set run time properties of SolverFactory and SolverManager
    BindResult<GreycosProperties> result =
        Binder.get(environment).bind("greycos", GreycosProperties.class);
    this.greycosProperties = result.orElseGet(GreycosProperties::new);
  }

  public <Solution_, ProblemId_> SolverManager<Solution_, ProblemId_> solverManagerSupplier(
      String solverConfigXml) {
    SolverFactory<Solution_> solverFactory =
        SolverFactory.create(solverConfigSupplier(solverConfigXml));
    SolverManagerConfig solverManagerConfig = new SolverManagerConfig();
    SolverManagerProperties solverManagerProperties = greycosProperties.getSolverManager();
    if (solverManagerProperties != null
        && solverManagerProperties.getParallelSolverCount() != null) {
      solverManagerConfig.setParallelSolverCount(solverManagerProperties.getParallelSolverCount());
    }
    return SolverManager.create(solverFactory, solverManagerConfig);
  }

  public SolverConfig solverConfigSupplier(String solverConfigXml) {
    SolverConfigIO solverConfigIO = new SolverConfigIO();
    return solverConfigIO.read(new StringReader(solverConfigXml));
  }
}
