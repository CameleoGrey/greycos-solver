package ai.greycos.solver.spring.boot.it;

import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.spring.boot.it.domain.IntegrationTestSolution;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integration-test")
public class GreycosSolverController {
  private final SolverFactory<IntegrationTestSolution> solverFactory;

  public GreycosSolverController(SolverFactory<IntegrationTestSolution> solverFactory) {
    this.solverFactory = solverFactory;
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public IntegrationTestSolution solve(@RequestBody IntegrationTestSolution problem) {
    return solverFactory.buildSolver().solve(problem);
  }
}
