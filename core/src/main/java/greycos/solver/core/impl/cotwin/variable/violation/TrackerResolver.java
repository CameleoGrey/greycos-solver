package greycos.solver.core.impl.cotwin.variable.violation;

import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;

public interface TrackerResolver<Solution_> {

  /**
   * Returns the {@link BasicVariableTracker} used to detect missing or incorrect variable listener
   * notifications for the given basic {@link PlanningVariable}. Used by {@link
   * EnvironmentMode#TRACKED_FULL_ASSERT}.
   *
   * @param variableDescriptor never null, must not describe a {@link PlanningListVariable}
   * @return never null
   */
  BasicVariableTracker<Solution_> getBasicVariableTracker(
      VariableDescriptor<Solution_> variableDescriptor);

  /**
   * Returns the {@link ListVariableTracker} used to detect missing or incorrect variable listener
   * notifications for the given {@link PlanningListVariable}. Used by {@link
   * EnvironmentMode#TRACKED_FULL_ASSERT}.
   *
   * @param variableDescriptor never null
   * @return never null
   */
  ListVariableTracker<Solution_> getListVariableTracker(
      ListVariableDescriptor<Solution_> variableDescriptor);
}
