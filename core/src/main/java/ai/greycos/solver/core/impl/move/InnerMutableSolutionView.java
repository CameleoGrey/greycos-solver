package ai.greycos.solver.core.impl.move;

import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import ai.greycos.solver.core.preview.api.move.MutableSolutionView;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface InnerMutableSolutionView<Solution_> extends MutableSolutionView<Solution_> {

  VariableDescriptorAwareScoreDirector<Solution_> getScoreDirector();
}
