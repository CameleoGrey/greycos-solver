package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.cotwin.lookup.LookUpManager;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

/**
 * Converts phase-local genomes into detached genuine-assignment snapshots. A batch owns one frozen
 * identity graph; its incidental assignments and shadows are never used as migrant state.
 */
public final class GeneticAlgorithmMigrationCodec<Solution_, Score_ extends Score<Score_>> {
  private final GeneticAlgorithmWorkspace<Solution_, Score_> workspace;
  private final InnerScoreDirector<Solution_, Score_> director;

  public GeneticAlgorithmMigrationCodec(
      GeneticAlgorithmWorkspace<Solution_, Score_> workspace,
      InnerScoreDirector<Solution_, Score_> director) {
    this.workspace = workspace;
    this.director = director;
  }

  public List<GeneticAlgorithmMigrationBatch.Entry<Solution_>> export(
      List<GeneticAlgorithmGenome> genomes, List<InnerScore<Score_>> scores) {
    if (genomes.size() != scores.size()) {
      throw new IllegalArgumentException("Migrant genomes and scores must have the same size.");
    }
    if (genomes.isEmpty()) return List.of();
    var descriptor = director.getSolutionDescriptor();
    var identityClone = director.cloneWorkingSolution();
    var lookup = new LookUpManager(descriptor.getLookUpStrategyResolver());
    descriptor.visitAll(identityClone, lookup::addWorkingObject);
    var exported = new ArrayList<GeneticAlgorithmMigrationBatch.Entry<Solution_>>(genomes.size());
    for (int member = 0; member < genomes.size(); member++) {
      var genome = genomes.get(member);
      var score = scores.get(member);
      if (!score.isFullyAssigned() || score.isStructurallyFlawed()) {
        throw new IllegalStateException("Cannot export an invalid migrant score (" + score + ").");
      }
      if (genome.size() != workspace.slots().size()
          || genome.listCount()
              != (workspace.listModel() == null ? 0 : workspace.listModel().ownerCount())) {
        throw new IllegalArgumentException("Migrant genome does not match its donor workspace.");
      }
      Map<GenuineVariableDescriptor<Solution_>, List<SolutionAssignments.BasicChangeRecord<?>>>
          basics = new LinkedHashMap<>();
      for (int slotId = 0; slotId < genome.size(); slotId++) {
        var slot = workspace.slots().get(slotId);
        basics
            .computeIfAbsent(slot.variableDescriptor(), ignored -> new ArrayList<>())
            .add(
                new SolutionAssignments.BasicChangeRecord<>(
                    lookup.lookUpWorkingObject(slot.entity()),
                    lookup.lookUpWorkingObject(genome.value(slotId))));
      }
      Map<ListVariableDescriptor<Solution_>, List<SolutionAssignments.ListChangeRecord<?>>> lists =
          new LinkedHashMap<>();
      var model = workspace.listModel();
      if (model != null) {
        var records = new ArrayList<SolutionAssignments.ListChangeRecord<?>>();
        for (int owner = 0; owner < genome.listCount(); owner++) {
          var values = new ArrayList<Object>();
          for (int id : genome.list(owner)) {
            values.add(lookup.lookUpWorkingObject(model.value(id)));
          }
          records.add(
              new SolutionAssignments.ListChangeRecord<>(
                  lookup.lookUpWorkingObject(model.owner(owner)), values));
        }
        lists.put(descriptor.getListVariableDescriptor(), records);
      }
      exported.add(
          new GeneticAlgorithmMigrationBatch.Entry<>(SolutionAssignments.of(basics, lists), score));
    }
    return List.copyOf(exported);
  }

  /** Reconstructs recipient-local slots and canonical list IDs without changing its graph. */
  public GeneticAlgorithmGenome importGenome(SolutionAssignments<Solution_> incoming) {
    var assignments = incoming.rebase(director);
    var values = workspace.genome().toArray();
    var seen = new boolean[values.length];
    var slotsByEntity = new IdentityHashMap<Object, Map<String, Integer>>();
    for (int index = 0; index < workspace.slots().size(); index++) {
      var slot = workspace.slots().get(index);
      slotsByEntity
          .computeIfAbsent(slot.entity(), ignored -> new LinkedHashMap<>())
          .put(slot.variableDescriptor().getVariableName(), index);
    }
    assignments
        .getBasicChanges()
        .forEach(
            (variable, records) -> {
              for (var record : records) {
                var slots = slotsByEntity.get(record.entity());
                var index = slots == null ? null : slots.get(variable.getVariableName());
                if (index == null || seen[index]) {
                  throw new IllegalArgumentException(
                      "Migrant contains an unknown or repeated basic assignment for ("
                          + variable.getSimpleEntityAndVariableName()
                          + ").");
                }
                seen[index] = true;
                values[index] = record.value();
              }
            });
    for (int index = 0; index < seen.length; index++) {
      if (!seen[index] && workspace.slots().get(index).movable()) {
        throw new IllegalArgumentException(
            "Migrant omits a movable basic assignment at slot (" + index + ").");
      }
    }
    var lists = workspace.genome().lists();
    var model = workspace.listModel();
    if (model == null) {
      if (!assignments.getListChanges().isEmpty()) {
        throw new IllegalArgumentException(
            "A basic-only recipient cannot import list assignments.");
      }
    } else {
      var owners = new IdentityHashMap<Object, Integer>();
      var ids = new IdentityHashMap<Object, Integer>();
      for (int owner = 0; owner < model.ownerCount(); owner++)
        owners.put(model.owner(owner), owner);
      for (int id = 0; id < model.valueCount(); id++) ids.put(model.value(id), id);
      var ownerSeen = new boolean[model.ownerCount()];
      assignments
          .getListChanges()
          .forEach(
              (variable, records) -> {
                if (!variable
                    .getVariableName()
                    .equals(
                        director
                            .getSolutionDescriptor()
                            .getListVariableDescriptor()
                            .getVariableName())) {
                  throw new IllegalArgumentException(
                      "Migrant list variable does not match the recipient.");
                }
                for (var record : records) {
                  var owner = owners.get(record.entity());
                  if (owner == null || ownerSeen[owner]) {
                    throw new IllegalArgumentException(
                        "Migrant contains an unknown or repeated list owner ("
                            + record.entity()
                            + ").");
                  }
                  ownerSeen[owner] = true;
                  var row = new int[record.values().size()];
                  for (int index = 0; index < row.length; index++) {
                    var id = ids.get(record.values().get(index));
                    if (id == null)
                      throw new IllegalArgumentException(
                          "Migrant list value is outside the recipient's canonical range.");
                    row[index] = id;
                  }
                  lists[owner] = row;
                }
              });
      for (int owner = 0; owner < ownerSeen.length; owner++) {
        if (!ownerSeen[owner] && model.ownerMovable(owner)) {
          throw new IllegalArgumentException(
              "Migrant omits movable list owner (" + model.owner(owner) + ").");
        }
      }
    }
    return new GeneticAlgorithmGenome(values, lists);
  }
}
