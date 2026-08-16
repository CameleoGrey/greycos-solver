package greycos.solver.core.impl.move;

import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import greycos.solver.core.preview.api.move.MutableSolutionView;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface InnerMutableSolutionView<Solution_> extends MutableSolutionView<Solution_> {

  VariableDescriptorAwareScoreDirector<Solution_> getScoreDirector();
}
