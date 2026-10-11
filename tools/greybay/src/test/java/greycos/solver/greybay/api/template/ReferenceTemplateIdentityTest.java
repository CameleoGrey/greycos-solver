package greycos.solver.greybay.api.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.alns.AlnsGrouping;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.ConfigurationTemplate;
import greycos.solver.greybay.config.Dimension;
import greycos.solver.greybay.config.FixedDimension;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.SearchSpace;
import greycos.solver.greybay.config.template.AlnsPortfolio;
import greycos.solver.greybay.config.template.LocalSearchVariant;
import greycos.solver.greybay.config.template.MovePortfolio;
import greycos.solver.greybay.config.template.TemplateParameters;
import greycos.solver.greybay.config.template.TemplateRequirements;

import org.junit.jupiter.api.Test;

class ReferenceTemplateIdentityTest {
  private static final ComponentIdentity TEMPLATE =
      new ComponentIdentity("template", "1", "application-v1");
  private static final ComponentIdentity PORTFOLIO =
      new ComponentIdentity("portfolio", "1", "portfolio-v1");
  private static final ComponentIdentity CALLBACK =
      new ComponentIdentity("grouping", "1", "grouping-v1");
  private static final TemplateRequirements REQUIREMENTS = TemplateRequirements.allModels();

  @Test
  void fixedTemperatureAndDeclaredArtifactParticipateInIdentity() {
    var baseline =
        localSearch(
            TEMPLATE, SimpleScore.of(10), Map.of("moves", moves(PORTFOLIO, REQUIREMENTS, 1.0)));
    var changedTemperature =
        localSearch(
            TEMPLATE, SimpleScore.of(20), Map.of("moves", moves(PORTFOLIO, REQUIREMENTS, 1.0)));
    var changedArtifact =
        localSearch(
            new ComponentIdentity("template", "1", "application-v2"),
            SimpleScore.of(10),
            Map.of("moves", moves(PORTFOLIO, REQUIREMENTS, 1.0)));
    assertThat(baseline.identity().id()).isEqualTo(TEMPLATE.id());
    assertThat(baseline.identity().version()).isEqualTo(TEMPLATE.version());
    assertThat(baseline.identity().artifactIdentity()).startsWith("sha256:");
    assertThat(baseline.identity())
        .isNotEqualTo(changedTemperature.identity())
        .isNotEqualTo(changedArtifact.identity());
  }

  @Test
  void moveControlsPortfolioIdentityAndRequirementsParticipateInIdentity() {
    var original = moves(PORTFOLIO, REQUIREMENTS, 1.0);
    var baseline = localSearch(TEMPLATE, SimpleScore.of(10), Map.of("moves", original));
    var changedModel =
        new TemplateRequirements(Set.of(TemplateRequirements.ModelKind.BASIC), Set.of());
    var changedFeature =
        new TemplateRequirements(REQUIREMENTS.models(), Set.of("approved-variable-scope"));
    for (var portfolio :
        List.of(
            moves(PORTFOLIO, REQUIREMENTS, 2.0),
            moves(new ComponentIdentity("portfolio", "1", "portfolio-v2"), REQUIREMENTS, 1.0),
            moves(PORTFOLIO, changedModel, 1.0),
            moves(PORTFOLIO, changedFeature, 1.0))) {
      assertThat(localSearch(TEMPLATE, SimpleScore.of(10), Map.of("moves", portfolio)).identity())
          .isNotEqualTo(baseline.identity());
    }
  }

  @Test
  void alnsOperatorsPortfolioCapabilitiesAndCallbackArtifactsParticipateInIdentity() {
    var baseline = alns(portfolio(PORTFOLIO, REQUIREMENTS, "eligible", CALLBACK, 1.0, 1.0));
    for (var portfolio :
        List.of(
            portfolio(PORTFOLIO, REQUIREMENTS, "eligible", CALLBACK, 2.0, 1.0),
            portfolio(PORTFOLIO, REQUIREMENTS, "eligible", CALLBACK, 1.0, 2.0),
            portfolio(
                new ComponentIdentity("portfolio", "1", "portfolio-v2"),
                REQUIREMENTS,
                "eligible",
                CALLBACK,
                1.0,
                1.0),
            portfolio(
                PORTFOLIO,
                new TemplateRequirements(REQUIREMENTS.models(), Set.of("scope")),
                "eligible",
                CALLBACK,
                1.0,
                1.0),
            portfolio(PORTFOLIO, REQUIREMENTS, "eligible-v2", CALLBACK, 1.0, 1.0),
            portfolio(
                PORTFOLIO,
                REQUIREMENTS,
                "eligible",
                new ComponentIdentity("grouping", "1", "grouping-v2"),
                1.0,
                1.0))) {
      var changed = alns(portfolio);
      assertThat(changed.searchSpace()).isEqualTo(baseline.searchSpace());
      assertThat(changed.identity()).isNotEqualTo(baseline.identity());
    }
  }

  @Test
  void pipelineIdentityIncludesChildrenWithUnchangedOuterIdentityAndSchema() {
    var originalFirst = alns(portfolio(PORTFOLIO, REQUIREMENTS, "eligible", CALLBACK, 1.0, 1.0));
    var changedFirst =
        alns(
            portfolio(
                PORTFOLIO,
                REQUIREMENTS,
                "eligible",
                new ComponentIdentity("grouping", "1", "grouping-v2"),
                1.0,
                1.0));
    var originalLast =
        localSearch(
            TEMPLATE, SimpleScore.of(10), Map.of("moves", moves(PORTFOLIO, REQUIREMENTS, 1.0)));
    var changedLast =
        localSearch(
            TEMPLATE, SimpleScore.of(20), Map.of("moves", moves(PORTFOLIO, REQUIREMENTS, 1.0)));
    var baseline = pipeline(originalFirst, originalLast);
    assertThat(pipeline(changedFirst, originalLast).searchSpace())
        .isEqualTo(baseline.searchSpace());
    assertThat(pipeline(changedFirst, originalLast).identity()).isNotEqualTo(baseline.identity());
    assertThat(pipeline(originalFirst, changedLast).identity()).isNotEqualTo(baseline.identity());
  }

  @Test
  void identityIgnoresMapAndCapabilitySetInsertionOrder() {
    var forward = new LinkedHashMap<String, MovePortfolio>();
    var reverse = new LinkedHashMap<String, MovePortfolio>();
    var firstRequirements =
        new TemplateRequirements(REQUIREMENTS.models(), new LinkedHashSet<>(List.of("a", "b")));
    var secondRequirements =
        new TemplateRequirements(REQUIREMENTS.models(), new LinkedHashSet<>(List.of("b", "a")));
    forward.put("moves", moves(PORTFOLIO, firstRequirements, 1.0));
    forward.put("other", moves(PORTFOLIO, REQUIREMENTS, 2.0));
    reverse.put("other", moves(PORTFOLIO, REQUIREMENTS, 2.0));
    reverse.put("moves", moves(PORTFOLIO, secondRequirements, 1.0));
    assertThat(localSearch(TEMPLATE, SimpleScore.of(10), forward).identity())
        .isEqualTo(localSearch(TEMPLATE, SimpleScore.of(10), reverse).identity());
  }

  @Test
  void orderedOperatorPortfoliosRetainTheirOrderInIdentity() {
    var original = portfolio(PORTFOLIO, REQUIREMENTS, "eligible", CALLBACK, 1.0, 1.0);
    var first = original.destroys().getFirst().withId("first");
    var second = original.destroys().getFirst().withId("second");
    var forward =
        new AlnsPortfolio(
            PORTFOLIO,
            List.of(first, second),
            original.repairs(),
            REQUIREMENTS,
            "eligible",
            original.callbacks());
    var reverse =
        new AlnsPortfolio(
            PORTFOLIO,
            List.of(second, first),
            original.repairs(),
            REQUIREMENTS,
            "eligible",
            original.callbacks());
    assertThat(alns(forward).identity()).isNotEqualTo(alns(reverse).identity());
  }

  @Test
  void callerScoreArrayMutationCannotChangeIdentityOrMaterialization() {
    long[] hard = {10};
    long[] soft = {20, 30};
    var score = BendableScore.of(hard, soft);
    var template =
        localSearch(TEMPLATE, score, Map.of("moves", moves(PORTFOLIO, REQUIREMENTS, 1.0)));
    var identity = template.identity();
    var expectedTemperature = score.toString();
    hard[0] = 100;
    score.softScores()[0] = 200;
    soft[1] = 300;
    assertThat(template.identity()).isSameAs(identity);
    var phase =
        (LocalSearchPhaseConfig)
            template
                .materialize(new SolverConfig(), template.defaultAssignment())
                .getPhaseConfigList()
                .getFirst();
    assertThat(phase.getAcceptorConfig().getSimulatedAnnealingStartingTemperature())
        .isEqualTo(expectedTemperature);
    assertThat(
            localSearch(TEMPLATE, score, Map.of("moves", moves(PORTFOLIO, REQUIREMENTS, 1.0)))
                .identity())
        .isNotEqualTo(identity);
  }

  private static MovePortfolio moves(
      ComponentIdentity identity, TemplateRequirements requirements, double weight) {
    return new MovePortfolio(
        identity, new ChangeMoveSelectorConfig().withFixedProbabilityWeight(weight), requirements);
  }

  private static ConfigurationTemplate localSearch(
      ComponentIdentity identity, Score<?> score, Map<String, MovePortfolio> moves) {
    return ReferenceTemplates.localSearch(
        parameters(
            identity,
            Map.of("ls.moves", "moves", "ls.acceptedCount", 1, "ls.sa.temperatureScale", 1.0)),
        LocalSearchVariant.SIMULATED_ANNEALING,
        moves,
        score);
  }

  private static ConfigurationTemplate alns(AlnsPortfolio portfolio) {
    return ReferenceTemplates.alns(
        parameters(
            TEMPLATE,
            Map.of(
                "alns.portfolio",
                "operators",
                "alns.acceptance",
                "HILL_CLIMBING",
                "alns.selection",
                "UCB",
                "alns.ucb.exploration",
                1.0,
                "alns.destroy.mode",
                "COUNT",
                "alns.destroy.minimumCount",
                1,
                "alns.destroy.maximumCount",
                2,
                "alns.repair.calculationLimit",
                10)),
        Map.of("operators", portfolio),
        null);
  }

  private static AlnsPortfolio portfolio(
      ComponentIdentity identity,
      TemplateRequirements requirements,
      String eligibility,
      ComponentIdentity callback,
      double destroyWeight,
      double repairWeight) {
    return new AlnsPortfolio(
        identity,
        List.of(
            new AlnsDestroyOperatorConfig()
                .withId("group")
                .withType(AlnsDestroyOperatorType.GROUP_REMOVAL)
                .withGroupingClass(Grouping.class)
                .withInitialWeight(destroyWeight)),
        List.of(
            new AlnsRepairOperatorConfig()
                .withId("greedy")
                .withType(AlnsRepairOperatorType.GREEDY)
                .withInitialWeight(repairWeight)),
        requirements,
        eligibility,
        Map.of(Grouping.class, callback));
  }

  private static ConfigurationTemplate pipeline(
      ConfigurationTemplate first, ConfigurationTemplate last) {
    return ReferenceTemplates.pipeline(
        TEMPLATE, first, last, new FixedDimension("pipeline.firstFraction", .5), .5);
  }

  private static TemplateParameters parameters(
      ComponentIdentity identity, Map<String, Object> values) {
    var dimensions =
        values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> (Dimension) new FixedDimension(entry.getKey(), entry.getValue()))
            .toList();
    return new TemplateParameters(
        identity, new SearchSpace("1", dimensions), ParameterAssignment.of(values));
  }

  public static final class Grouping implements AlnsGrouping<Object> {
    @Override
    public Object groupKey(Object solution, AlnsTarget<Object> target) {
      return "group";
    }
  }
}
