package greycos.solver.greybay.qualification;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.api.GreybayStudies;
import greycos.solver.greybay.api.QualificationResult;
import greycos.solver.greybay.api.StudyResult;
import greycos.solver.greybay.config.StudyRunLimits;

/** Opt-in bridge to real studies. Unit tests never invoke this runner. */
public final class GreybayCampaignRunner {
  private GreybayCampaignRunner() {}

  public static <Solution_, Score_ extends Score<Score_>>
      CampaignHarness.Runner<Solution_, Score_> live() {
    return (request, protocol, directory) -> {
      StudyResult<Score_> search;
      try (var study = GreybayStudies.create(directory, request.definition())) {
        try {
          study.run();
        } catch (RuntimeException failure) {
          return new CampaignHarness.Evidence<>(
              study.snapshot(),
              study.snapshot(),
              null,
              "Search failed: " + failure.getClass().getName() + ": " + failure.getMessage());
        }
        search = study.snapshot();
      }
      if (!search.hasEligibleIncumbent()) {
        return new CampaignHarness.Evidence<>(
            search,
            search,
            null,
            "No eligible training incumbent; qualification unavailable and no replacement run is selected.");
      }
      // The qualification allocation starts from cumulative consumed charge. Unused search
      // allocation
      // is never transferred to held-out evaluation. Restart warmup, verification and export are
      // charged.
      long qualificationCap =
          Math.addExact(
              search.admissionChargedNanos(), protocol.qualificationAllocation().toNanos());
      var limits =
          new StudyRunLimits(
              protocol.maximumTrials(),
              Duration.ofNanos(Math.max(protocol.searchAllocation().toNanos(), qualificationCap)));
      long qualificationStarted = System.nanoTime();
      try (var study = GreybayStudies.resume(directory, request.definition(), limits)) {
        // Resume cannot decrease the old global cap. The additional elapsed-active watchdog
        // enforces Q even if a small finite search space exhausted long before its S cap.
        long remaining =
            remainingNanos(
                qualificationStarted,
                System.nanoTime(),
                protocol.qualificationAllocation().toNanos());
        if (remaining == 0) {
          return new CampaignHarness.Evidence<>(
              search,
              study.snapshot(),
              null,
              "Qualification allocation exhausted during resume setup; held-out evaluation was not admitted.");
        }
        try (var watchdog =
            Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                  var thread = new Thread(runnable, "greybay-campaign-qualification-budget");
                  thread.setDaemon(true);
                  return thread;
                })) {
          var cancellation =
              watchdog.schedule(
                  study::requestStop,
                  remainingNanos(
                      qualificationStarted,
                      System.nanoTime(),
                      protocol.qualificationAllocation().toNanos()),
                  TimeUnit.NANOSECONDS);
          QualificationResult<Score_> qualification = null;
          String detail =
              "Coordinator active accounting includes setup, warmup, failed attempts, sampling, recovery, "
                  + "verification, held-out evaluation and export. The qualification watchdog includes resume setup; "
                  + "outer campaign freeze/report orchestration is separate.";
          try {
            qualification = study.qualify();
            if (remainingNanos(
                        qualificationStarted,
                        System.nanoTime(),
                        protocol.qualificationAllocation().toNanos())
                    > 0
                && study.snapshot().frozenSelectionTrialId() != null) {
              study.exportSelected(directory.resolve("export"));
            }
          } catch (RuntimeException failure) {
            detail +=
                " Qualification/export failed or was interrupted: "
                    + failure.getClass().getName()
                    + ": "
                    + failure.getMessage()
                    + ". Retain the study journal and existing observations.";
          } finally {
            cancellation.cancel(false);
            watchdog.shutdown();
          }
          return new CampaignHarness.Evidence<>(search, study.snapshot(), qualification, detail);
        }
      }
    };
  }

  static long remainingNanos(long started, long now, long allowance) {
    long elapsed = now - started;
    if (elapsed < 0) {
      throw new IllegalArgumentException(
          "Qualification elapsed time must fit positive monotonic nanosecond arithmetic.");
    }
    return elapsed >= allowance ? 0 : allowance - elapsed;
  }
}
