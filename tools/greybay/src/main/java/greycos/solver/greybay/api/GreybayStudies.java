package greycos.solver.greybay.api;

import java.nio.file.Path;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.StudyRunLimits;
import greycos.solver.greybay.impl.study.DefaultGreybayStudy;

public final class GreybayStudies {
  private GreybayStudies() {}

  public static <Solution_, Score_ extends Score<Score_>> GreybayStudy<Solution_, Score_> create(
      Path directory, GreybayStudyDefinition<Solution_, Score_> definition) {
    return DefaultGreybayStudy.open(directory, definition, definition.runLimits(), false);
  }

  public static <Solution_, Score_ extends Score<Score_>> GreybayStudy<Solution_, Score_> resume(
      Path directory, GreybayStudyDefinition<Solution_, Score_> definition, StudyRunLimits limits) {
    return DefaultGreybayStudy.open(directory, definition, limits, true);
  }

  public static <Solution_, Score_ extends Score<Score_>> GreybayStudy<Solution_, Score_> resume(
      Path directory, GreybayStudyDefinition<Solution_, Score_> definition) {
    return resume(directory, definition, definition.runLimits());
  }
}
