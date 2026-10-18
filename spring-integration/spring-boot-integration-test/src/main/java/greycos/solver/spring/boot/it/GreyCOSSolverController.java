package greycos.solver.spring.boot.it;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisOptions;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.spring.boot.it.cotwin.IntegrationTestSolution;
import greycos.solver.spring.boot.it.cotwin.TestdataMultiListFixture;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integration-test")
public class GreyCOSSolverController {
  private final SolverFactory<IntegrationTestSolution> solverFactory;

  public GreyCOSSolverController(SolverFactory<IntegrationTestSolution> solverFactory) {
    this.solverFactory = solverFactory;
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public IntegrationTestSolution solve(@RequestBody IntegrationTestSolution problem) {
    return solverFactory.buildSolver().solve(problem);
  }

  @PostMapping(
      value = "/indictments",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ScoreAnalysis<SimpleScore> analyzeIndictments(
      @RequestBody IntegrationTestSolution problem) {
    return SolutionManager.<IntegrationTestSolution, SimpleScore>create(solverFactory)
        .analyzeWithIndictments(problem, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL);
  }

  @PostMapping(
      value = "/raw-analysis",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ScoreAnalysis<SimpleScore> analyzeRaw(@RequestBody IntegrationTestSolution problem) {
    return SolutionManager.<IntegrationTestSolution, SimpleScore>create(solverFactory)
        .analyzeWithOptions(
            problem,
            new ScoreAnalysisOptions(
                ScoreAnalysisFetchPolicy.FETCH_ALL,
                IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL,
                true));
  }

  @PostMapping(value = "/multiple-lists", produces = MediaType.TEXT_PLAIN_VALUE)
  public String solveMultipleLists() {
    var problem = new IntegrationTestSolution(List.of(), List.of());
    TestdataMultiListFixture.initialize(problem);
    var solution = solverFactory.buildSolver().solve(problem);
    TestdataMultiListFixture.verify(problem, solution);
    return solution.getScore() + ";lists=3/4;pins=1/2;shadows=valid;clone=independent";
  }
}
