package greycos.solver.core.api.solver.multistage;

/**
 * An opaque operation created by a multistage evaluator. Applications must obtain operations from
 * their active evaluator; application implementations of this interface are unsupported.
 *
 * <p>An operation belongs to the candidate and stage that created it. It must not be retained for
 * another stage, candidate, working solution or thread. Successfully evaluating an operation does
 * not consume it: the stage may evaluate alternatives and then select one of them. Aborting the
 * candidate invalidates its operations; see {@link MultistageMoveEvaluator} for the distinction
 * between recoverable validation rejections and failures that abort evaluation.
 *
 * @param <Solution_> the planning solution type
 */
public interface MultistageOperation<Solution_> {}
