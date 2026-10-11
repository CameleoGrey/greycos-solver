package greycos.solver.greybay.qualification;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.api.ProblemAdapter;
import greycos.solver.greybay.api.objective.BalancedQualityObjective;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.ProblemPanel;
import greycos.solver.greybay.config.RandomSamplerConfig;
import greycos.solver.greybay.config.ResourcePolicy;
import greycos.solver.greybay.config.TpeSamplerConfig;
import greycos.solver.greybay.examples.GreybayWorkflowExample;
import greycos.solver.greybay.examples.RoutingExample;
import greycos.solver.greybay.examples.SchedulingExample;

/** Explicit launcher; never discovered or executed as a unit test. */
public final class GreybayCampaignMain {
  private GreybayCampaignMain() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 11
        || args.length > 12
        || !(args[0].equals("freeze") || args[0].equals("run"))) {
      throw new IllegalArgumentException(
          "Usage: GreybayCampaignMain <freeze|run> <scheduling|routing> "
              + "<new-output-directory> <solveMillis> <searchActiveMillis> <qualificationActiveMillis> "
              + "<warmupMillis> <maxTrials> <bootstrapSeed> <resourceLabel> <reportingCriteria> [optimizerSeedsCsv]");
    }
    var optimizerSeeds =
        args.length == 12
            ? Arrays.stream(args[11].split(",", -1)).map(Long::parseLong).toList()
            : CampaignHarness.Protocol.referenceOptimizerSeeds();
    var sizes = new LinkedHashMap<String, CampaignHarness.Size>();
    for (var split : List.of("train", "heldout")) {
      for (var size : CampaignHarness.Size.values()) {
        sizes.put(split + "-" + size.name().toLowerCase(java.util.Locale.ROOT), size);
      }
    }
    var model =
        switch (args[1]) {
          case "scheduling" -> CampaignHarness.Model.BASIC_SCHEDULING;
          case "routing" -> CampaignHarness.Model.LIST_ROUTING;
          default -> throw new IllegalArgumentException("Unknown campaign family: " + args[1]);
        };
    var protocol =
        new CampaignHarness.Protocol(
            "example-" + args[1],
            model,
            sizes,
            optimizerSeeds,
            Duration.ofMillis(Long.parseLong(args[4])),
            Duration.ofMillis(Long.parseLong(args[5])),
            Long.parseLong(args[7]),
            Long.parseLong(args[8]),
            args[10]);
    if (model == CampaignHarness.Model.BASIC_SCHEDULING) {
      launch(
          args, protocol, SchedulingExample.baseSolverConfig(), SchedulingExample.problemAdapter());
    } else {
      launch(args, protocol, RoutingExample.baseSolverConfig(), RoutingExample.problemAdapter());
    }
  }

  private static <Solution_> void launch(
      String[] args,
      CampaignHarness.Protocol protocol,
      SolverConfig base,
      ProblemAdapter<Solution_> adapter)
      throws Exception {
    var horizon = Duration.ofMillis(Long.parseLong(args[3]));
    var warmup = Duration.ofMillis(Long.parseLong(args[6]));
    var resources =
        new ResourcePolicy(
            "NONE",
            false,
            Map.of(
                "resourceLabel",
                args[9],
                "javaRuntime",
                System.getProperty("java.runtime.version"),
                "os",
                System.getProperty("os.name"),
                "architecture",
                System.getProperty("os.arch")));
    var frozen =
        CampaignHarness.freeze(
            protocol,
            (method, seed) ->
                GreybayStudyDefinition.<Solution_, HardSoftScore>builder()
                    .baseSolverConfig(base)
                    .problemAdapter(adapter)
                    .trainingPanel(
                        new ProblemPanel(
                            List.of("train-small", "train-medium", "train-large"),
                            List.of(11L, 17L, 23L)))
                    .heldOutPanel(
                        new ProblemPanel(
                            List.of("heldout-small", "heldout-medium", "heldout-large"),
                            List.of(101L, 107L, 113L)))
                    .templates(List.of(GreybayWorkflowExample.template()))
                    .objective(new BalancedQualityObjective<>())
                    .sampler(
                        method == CampaignHarness.Method.TPE
                            ? new TpeSamplerConfig().buildSampler()
                            : new RandomSamplerConfig().buildSampler())
                    .optimizerSeed(seed)
                    .solveDuration(horizon)
                    .warmupDuration(warmup)
                    .resourcePolicy(resources)
                    .maxTrials(protocol.maximumTrials())
                    .activeTimeLimit(protocol.searchAllocation())
                    .build());
    if (args[0].equals("freeze")) {
      System.out.println(new String(frozen.manifestBytes(), StandardCharsets.UTF_8));
      return;
    }
    var results = frozen.execute(Path.of(args[2]), GreybayCampaignRunner.live());
    System.out.println(
        "Recorded "
            + results.size()
            + " method/optimizer invocations in "
            + args[2]
            + ". Read all negative, incomplete and failed outcomes before interpreting this family/horizon.");
  }
}
