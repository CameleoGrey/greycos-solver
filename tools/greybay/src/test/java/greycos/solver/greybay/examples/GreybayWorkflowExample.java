package greycos.solver.greybay.examples;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.api.ConfigurationTemplate;
import greycos.solver.greybay.api.GreybayStudies;
import greycos.solver.greybay.api.ProblemAdapter;
import greycos.solver.greybay.api.VerificationResult;
import greycos.solver.greybay.api.objective.BalancedQualityObjective;
import greycos.solver.greybay.api.template.ReferenceTemplates;
import greycos.solver.greybay.config.FixedDimension;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.IntegerDimension;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.ProblemPanel;
import greycos.solver.greybay.config.SearchSpace;
import greycos.solver.greybay.config.StudyRunLimits;
import greycos.solver.greybay.config.TpeSamplerConfig;
import greycos.solver.greybay.config.template.LocalSearchVariant;
import greycos.solver.greybay.config.template.MovePortfolio;
import greycos.solver.greybay.config.template.TemplateParameters;
import greycos.solver.greybay.config.template.TemplateRequirements;

/**
 * Runnable correctness walkthrough; its short startup trials are not an effectiveness experiment.
 */
public final class GreybayWorkflowExample {
  private GreybayWorkflowExample() {}

  public static ConfigurationTemplate template() {
    var space =
        new SearchSpace(
            List.of(
                new FixedDimension("ls.moves", "automatic"),
                new IntegerDimension("ls.acceptedCount", 1, 8, 1),
                new IntegerDimension("ls.la.history", 4, 64, 4)));
    var defaults =
        ParameterAssignment.of(
            Map.of("ls.moves", "automatic", "ls.acceptedCount", 1, "ls.la.history", 8));
    return ReferenceTemplates.localSearch(
        new TemplateParameters(ExampleData.identity("late-acceptance-template"), space, defaults),
        LocalSearchVariant.LATE_ACCEPTANCE,
        Map.of(
            "automatic",
            MovePortfolio.automatic(
                ExampleData.identity("automatic-moves"), TemplateRequirements.allModels())),
        null);
  }

  public static <Solution_> GreybayStudyDefinition<Solution_, HardSoftScore> definition(
      SolverConfig base, ProblemAdapter<Solution_> adapter, long trials, Duration horizon) {
    return GreybayStudyDefinition.<Solution_, HardSoftScore>builder()
        .baseSolverConfig(base)
        .problemAdapter(adapter)
        .trainingPanel(new ProblemPanel(List.of("train-small"), List.of(11L)))
        .heldOutPanel(new ProblemPanel(List.of("heldout-small"), List.of(101L)))
        .templates(List.of(template()))
        .objective(new BalancedQualityObjective<>())
        .sampler(new TpeSamplerConfig().buildSampler())
        .optimizerSeed(1701L)
        .solveDuration(horizon)
        .warmupDuration(Duration.ZERO)
        .maxTrials(trials)
        .activeTimeLimit(Duration.ofMinutes(5))
        .build();
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException(
          "Usage: GreybayWorkflowExample <scheduling|routing> <new-study-directory>");
    }
    var directory = Path.of(args[1]);
    switch (args[0]) {
      case "scheduling" ->
          run(directory, SchedulingExample.baseSolverConfig(), SchedulingExample.problemAdapter());
      case "routing" ->
          run(directory, RoutingExample.baseSolverConfig(), RoutingExample.problemAdapter());
      default -> throw new IllegalArgumentException("Unknown family (" + args[0] + ").");
    }
  }

  private static <Solution_> void run(
      Path directory, SolverConfig base, ProblemAdapter<Solution_> adapter) {
    var definition = definition(base, adapter, 1, Duration.ofSeconds(1));
    try (var study = GreybayStudies.create(directory, definition)) {
      var first = study.run();
      System.out.println(
          "First segment: "
              + first.trainingStatus()
              + ", committed trials="
              + first.trials().size());
      study.requestStop();
    }
    try (var study =
        GreybayStudies.resume(directory, definition, StudyRunLimits.of(2, Duration.ofMinutes(6)))) {
      var training = study.run();
      if (!training.hasEligibleIncumbent()) {
        throw new IllegalStateException("No eligible incumbent: " + training.trainingStatus());
      }
      var qualification = study.qualify();
      var exported = study.exportSelected(directory.resolve("selected"));
      System.out.println(
          "Qualification: "
              + qualification.status()
              + ", comparison="
              + qualification.comparison());
      System.out.println("Configuration: " + exported.solverConfigPath());
      var reloaded = SolverConfig.createFromXmlFile(exported.solverConfigPath().toFile());
      var solved =
          SolverFactory.<Solution_>create(reloaded)
              .buildSolver()
              .solve(adapter.loadFresh("heldout-small"));
      var replay = adapter.verifier().orElseThrow().verify("heldout-small", solved);
      if (replay.status() != VerificationResult.Status.PASSED) {
        throw new IllegalStateException(
            "Exported configuration replay failed: " + replay.message());
      }
      System.out.println(
          "Export reload: "
              + replay.status()
              + "; this extra demonstration solve is outside the study's accounted observations.");
    }
  }
}
