package ai.greycos.solver.core.impl.cotwin.solution.cloner.gizmo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import io.quarkus.gizmo2.ClassOutput;

record GizmoSolutionClonerClassOutput(Map<String, byte[]> classNameToBytecode)
    implements ClassOutput {
  private static final String CLASS_FILE_SUFFIX = ".class";

  @Override
  public void write(String classFileLocation, byte[] byteCode) {
    var className =
        classFileLocation
            .substring(0, classFileLocation.length() - CLASS_FILE_SUFFIX.length())
            .replace('/', '.');
    classNameToBytecode.put(className, byteCode);
    if (GizmoSolutionClonerImplementor.DEBUG) {
      Path debugRoot = Paths.get("target/greycos-solver-generated-classes");
      Path rest = Paths.get(classFileLocation);
      Path destination = debugRoot.resolve(rest);

      try {
        Files.createDirectories(destination.getParent());
        Files.write(destination, byteCode);
      } catch (IOException e) {
        throw new IllegalStateException(
            "Fail to write debug class file (%s).".formatted(destination), e);
      }
    }
  }
}
