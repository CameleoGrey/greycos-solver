package greycos.solver.greybay.api;

import java.nio.file.Path;

import greycos.solver.core.api.score.Score;

/**
 * One serial, durable study. Run, qualify and export are synchronous and mutually exclusive.
 * requestStop and snapshot are thread-safe. close requests stop and waits for all owned work before
 * releasing the study-directory lock, including when a custom solver callback is blocked.
 */
public interface GreybayStudy<Solution_, Score_ extends Score<Score_>> extends AutoCloseable {
  StudyResult<Score_> run();

  void requestStop();

  StudyResult<Score_> snapshot();

  QualificationResult<Score_> qualify();

  ExportResult exportSelected(Path destination);

  @Override
  void close();
}
