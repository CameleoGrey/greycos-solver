package greycos.solver.core.preview.api.cotwin.metamodel;

import greycos.solver.core.api.cotwin.variable.PlanningVariable;

import org.jspecify.annotations.NullMarked;

/**
 * A {@link VariableMetaModel} that represents a @{@link PlanningVariable basic planning variable}.
 *
 * <p><strong>This package and all of its contents are part of the Neighborhoods API, which is under
 * development and is only offered as a preview feature.</strong> There are no guarantees for
 * backward compatibility; any class, method, or field may change or be removed without prior
 * notice, although we will strive to avoid this as much as possible.
 *
 * <p>We encourage you to try the API and give us feedback on your experience with it, before we
 * finalize the API. Please direct your feedback to the <a
 * href="https://github.com/CameleoGrey/greycos-solver/discussions">GreyCOS Solver GitHub
 * discussions</a>.
 *
 * @param <Solution_> the solution type
 * @param <Entity_> the entity type
 * @param <Value_> the value type
 */
@NullMarked
public sealed interface GenuineVariableMetaModel<Solution_, Entity_, Value_>
    extends VariableMetaModel<Solution_, Entity_, Value_>
    permits PlanningVariableMetaModel, PlanningListVariableMetaModel {}
