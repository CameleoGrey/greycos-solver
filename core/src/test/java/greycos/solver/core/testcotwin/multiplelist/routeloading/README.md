# Route, loading order and quantities

This runnable example uses two independently optimized lists on each vehicle: `visits` and
`loadingOrder`. Their element types are distinct. Each cargo allocation also has a discrete quantity
and an assignment to a fixed compartment. The source stays on the core test classpath so it can share
the solver's verification lifecycle without adding a production example dependency or reactor module.

The business input and accepted-decision records have no solver annotations. The mutable
`RouteLoadingSolution` contains the planning fields and list shadows. It reconstructs decisions by
stable IDs. `RouteLoadingReplay` accepts only those decisions and the original immutable input;
it never reads list shadows or calls the constraint provider.

## Business rules

All visits and precreated cargo allocations occur in their respective lists exactly once. Quantity
is an integer from zero through `maximumQuantity`. A zero quantity is an inactive allocation: it
retains a list position but contributes no loading, compatibility or access penalty. Each visit has
one or more precreated allocations, allowing demand to split between compatible compartments on its
vehicle. The example does not create allocations during solving.

Hard penalties count demand discrepancies in units, excess departure capacity in units, and one
violation each for an active cargo allocation on the wrong vehicle, a compartment on the wrong
vehicle, or an incompatible product. Compartments are fixed facts and are not list owners or list
elements. Deliveries only decrease the load; replay also walks every delivery event and validates
the remaining load for feasible plans. Pickups, time windows, continuous quantities and physical
three-dimensional packing are outside this example's business model.

Travel uses Manhattan distance from and back to a depot at `(0, 0)`. Earlier cargo entries are loaded
deeper. Unloading an earlier entry while a later entry for a later delivery remains in the same
compartment costs three units per blocking quantity. A separate loading-priority preference costs
one unit per out-of-order active pair. Thus loading order is an independent decision with a tradeoff;
it is not a derived reversal of the route. Soft score is the negative sum of travel, obstruction,
and loading-priority penalties.

## Run

From the repository root, compile and validate the focused tests:

```bash
./mvnw -B -ntp -pl core -am -Dspotless.goal=check \
  -Dtest=MultipleListDefaultSolverTest,RouteLoadingExampleTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -B -ntp -pl core -DincludeScope=test \
  -Dmdep.outputFile=target/route-loading-classpath.txt dependency:build-classpath
java -cp "core/target/test-classes:core/target/classes:$(cat core/target/route-loading-classpath.txt)" \
  greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingExample 0 2000
```

The optional arguments are random seed and solver step limit. The example uses the automatically
generated phases and selectors, with `FULL_ASSERT`. Output labels the returned plan `FEASIBLE` or
`DRAFT` according to independent replay, prints all decisions, and verifies replay score equality.
A bounded solve is not an optimality certificate.

`RouteLoadingExampleTest` enumerates all 36 decisions of a tiny one-vehicle case, including infeasible
quantity assignments. Four are feasible and the independently calculated optimum is `0hard/-6soft`.
The test compares every state's independently calculated score with the ConstraintProvider. Additional tests cover incremental cross-list changes, basic-variable changes,
restoration, returned-solution cloning, shadow consistency and malformed replay input.

## Measurement protocol

Keep baseline and changed builds separate, and record commit, JDK, hardware, heap, seeds, input sizes,
list population sizes, quantity range, selectors and workers. Compare existing single-list and mixed
models first using fixed legal traces or fixed logical work. Record setup cost, score calculations,
elapsed time, allocation/GC and memory separately from final native score. Use paired seeds and equal
time budgets for solution-quality comparisons. Report feasibility and time to feasibility, not just
evaluations per second. Results are measurements with workload limits; there is no hard performance
acceptance threshold for this change. Correctness, replay and state parity remain required gates.
