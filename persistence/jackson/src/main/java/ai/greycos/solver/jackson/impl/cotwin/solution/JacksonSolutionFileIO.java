package ai.greycos.solver.jackson.impl.cotwin.solution;

import java.io.File;
import java.io.InputStream;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.SolutionFileIO;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class JacksonSolutionFileIO<Solution_> implements SolutionFileIO<Solution_> {

  private final Class<Solution_> clazz;
  private final ObjectMapper mapper;

  public JacksonSolutionFileIO(Class<Solution_> clazz) {
    // Loads GreyCOSJacksonModule via ServiceLoader, as well as any other Jackson modules on the
    // classpath.
    this(clazz, JsonMapper.builder().findAndAddModules().build());
  }

  public JacksonSolutionFileIO(Class<Solution_> clazz, ObjectMapper mapper) {
    this.clazz = clazz;
    this.mapper = mapper;
  }

  @Override
  public String getInputFileExtension() {
    return "json";
  }

  @Override
  public String getOutputFileExtension() {
    return "json";
  }

  @Override
  public Solution_ read(File inputSolutionFile) {
    try {
      return mapper.readValue(inputSolutionFile, clazz);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(
          "Failed reading inputSolutionFile (" + inputSolutionFile + ").", e);
    }
  }

  public Solution_ read(InputStream inputSolutionStream) {
    try {
      return mapper.readValue(inputSolutionStream, clazz);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed reading inputSolutionStream.", e);
    }
  }

  @Override
  public void write(Solution_ solution, File file) {
    try {
      mapper.writerWithDefaultPrettyPrinter().writeValue(file, solution);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed write", e);
    }
  }
}
