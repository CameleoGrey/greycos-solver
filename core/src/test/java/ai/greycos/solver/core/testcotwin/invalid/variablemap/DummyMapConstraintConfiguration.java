package ai.greycos.solver.core.testcotwin.invalid.variablemap;

import java.util.AbstractMap;
import java.util.Set;

import ai.greycos.solver.core.api.cotwin.constraintweight.ConstraintConfiguration;

@ConstraintConfiguration
public class DummyMapConstraintConfiguration extends AbstractMap<String, String> {

  @Override
  public Set<Entry<String, String>> entrySet() {
    return Set.of();
  }
}
