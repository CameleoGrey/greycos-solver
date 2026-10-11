package greycos.solver.greybay.api.template;

import java.util.Map;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.ConfigurationTemplate;
import greycos.solver.greybay.config.Dimension;
import greycos.solver.greybay.config.template.AlnsPortfolio;
import greycos.solver.greybay.config.template.LocalSearchVariant;
import greycos.solver.greybay.config.template.MovePortfolio;
import greycos.solver.greybay.config.template.TemplateParameters;
import greycos.solver.greybay.impl.template.ReferenceTemplate;

/**
 * Curated phase templates with application-owned bounds and portfolios. All dimensions may be
 * fixed.
 *
 * <p>Ordinary LS requires {@code ls.moves}, {@code ls.acceptedCount}, and the variant control:
 * {@code ls.la.history}, {@code ls.tabu.size}, {@code ls.tabu.ratio}, {@code
 * ls.sa.temperatureScale}, {@code ls.gd.incrementRatio}, or {@code ls.gd.incrementScale}. Score
 * scale controls multiply the supplied score using {@link Score#multiply(double)}. SA requires
 * finite positive levels; Great Deluge requires a finite positive score increment. Both require
 * zero structural score, and the scaled result is validated again after multiplication.
 *
 * <p>GLS requires {@code gls.moves}, {@code gls.penaltyFactor} (real or exact decimal category),
 * {@code gls.sampleSize}, {@code gls.focusStepLimit}, {@code gls.focusPenaltyUpdateLimit}, {@code
 * gls.maxPenaltyUpdatesPerStep}, {@code gls.excursionStepLimit}, {@code
 * gls.excursionRepairStepLimit}, and {@code gls.maxUnproductiveRounds}.
 *
 * <p>ALNS requires {@code alns.portfolio}, {@code alns.destroy.mode} (COUNT or PERCENTAGE), its
 * conditional {@code alns.destroy.minimumCount}/{@code maximumCount} or {@code
 * alns.destroy.minimumPercentage}/{@code maximumPercentage}, {@code alns.repair.calculationLimit},
 * {@code alns.acceptance} (core enum name), and {@code alns.selection} (core enum name). LA adds
 * {@code alns.la.history}; SA adds {@code alns.sa.temperatureScale} and {@code
 * alns.sa.coolingRate}. Roulette adds {@code alns.roulette.segmentLength}, {@code
 * alns.roulette.reactionFactor}, and {@code alns.roulette.minimumWeight}; UCB adds {@code
 * alns.ucb.exploration}. Optional shared controls are {@code alns.newBestReward}, {@code
 * alns.improvedReward}, {@code alns.acceptedReward}, and {@code alns.recoveryCount}. {@code
 * alns.repair.regretK} and {@code alns.repair.topK} are active only when the selected portfolio
 * includes the corresponding REGRET_K or RANDOMIZED_GREEDY repair.
 *
 * <p>Applications must express these branches with matching activation predicates; inactive values
 * are rejected. The returned identity preserves the declared ID and version and derives its
 * artifact identity from the declaration, fixed score snapshot, portfolios, capabilities,
 * callbacks, schema, defaults, and pipeline children. Changes to these inputs therefore invalidate
 * study resume even when an application reuses its declared template identity.
 */
public final class ReferenceTemplates {
  private ReferenceTemplates() {}

  public static ConfigurationTemplate localSearch(
      TemplateParameters parameters,
      LocalSearchVariant variant,
      Map<String, MovePortfolio> moves,
      Score<?> referenceScore) {
    return ReferenceTemplate.localSearch(parameters, variant, moves, referenceScore);
  }

  public static ConfigurationTemplate guidedLocalSearch(
      TemplateParameters parameters, Map<String, MovePortfolio> moves) {
    return ReferenceTemplate.guidedLocalSearch(parameters, moves);
  }

  public static ConfigurationTemplate alns(
      TemplateParameters parameters, Map<String, AlnsPortfolio> portfolios, Score<?> temperature) {
    return ReferenceTemplate.alns(parameters, portfolios, temperature);
  }

  /**
   * A best-solution handoff from ALNS or GLS into ordinary LS. The fraction dimension must be named
   * {@code pipeline.firstFraction}. The coordinator installs floor(fraction * whole horizon millis)
   * as the first search phase's cap; construction time is not subtracted before computing this cap.
   */
  public static ConfigurationTemplate pipeline(
      ComponentIdentity identity,
      ConfigurationTemplate first,
      ConfigurationTemplate localSearch,
      Dimension firstFraction,
      Object defaultFraction) {
    return ReferenceTemplate.pipeline(identity, first, localSearch, firstFraction, defaultFraction);
  }
}
