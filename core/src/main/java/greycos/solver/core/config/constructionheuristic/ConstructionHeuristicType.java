package greycos.solver.core.config.constructionheuristic;

import jakarta.xml.bind.annotation.XmlEnum;

import greycos.solver.core.config.heuristic.selector.entity.EntitySorterManner;
import greycos.solver.core.config.heuristic.selector.value.ValueSorterManner;

import org.jspecify.annotations.NullMarked;

@NullMarked
@XmlEnum
public enum ConstructionHeuristicType {
  /** A specific form of {@link #ALLOCATE_ENTITY_FROM_QUEUE}. */
  FIRST_FIT,
  /** A specific form of {@link #ALLOCATE_ENTITY_FROM_QUEUE}. */
  FIRST_FIT_DECREASING,
  /** A specific form of {@link #ALLOCATE_ENTITY_FROM_QUEUE}. */
  WEAKEST_FIT,
  /** A specific form of {@link #ALLOCATE_ENTITY_FROM_QUEUE}. */
  WEAKEST_FIT_DECREASING,
  /** A specific form of {@link #ALLOCATE_ENTITY_FROM_QUEUE}. */
  STRONGEST_FIT,
  /** A specific form of {@link #ALLOCATE_ENTITY_FROM_QUEUE}. */
  STRONGEST_FIT_DECREASING,
  /**
   * Put all entities in a queue. Assign the first entity (from that queue) to the best value.
   * Repeat until all entities are assigned.
   */
  ALLOCATE_ENTITY_FROM_QUEUE,
  /**
   * Put all values in a round-robin queue. Assign the best entity to the first value (from that
   * queue). Repeat until all entities are assigned.
   */
  ALLOCATE_TO_VALUE_FROM_QUEUE,
  /** A specific form of {@link #ALLOCATE_FROM_POOL}. */
  CHEAPEST_INSERTION,
  /**
   * Put all entity-value combinations in a pool. Assign the best entity to best value. Repeat until
   * all entities are assigned.
   */
  ALLOCATE_FROM_POOL,
  /**
   * Independently randomize all movable basic assignments and list membership/order, preserving
   * pins. Constructs one solution without comparing candidate scores, including on warm input.
   */
  RANDOM_ASSIGNMENT;

  public EntitySorterManner getDefaultEntitySorterManner() {
    return switch (this) {
      case FIRST_FIT, WEAKEST_FIT, STRONGEST_FIT, RANDOM_ASSIGNMENT -> EntitySorterManner.NONE;
      case FIRST_FIT_DECREASING, WEAKEST_FIT_DECREASING, STRONGEST_FIT_DECREASING ->
          EntitySorterManner.DESCENDING;
      case ALLOCATE_ENTITY_FROM_QUEUE,
          ALLOCATE_TO_VALUE_FROM_QUEUE,
          CHEAPEST_INSERTION,
          ALLOCATE_FROM_POOL ->
          EntitySorterManner.DESCENDING_IF_AVAILABLE;
    };
  }

  public ValueSorterManner getDefaultValueSorterManner() {
    return switch (this) {
      case FIRST_FIT, FIRST_FIT_DECREASING, RANDOM_ASSIGNMENT -> ValueSorterManner.NONE;
      case WEAKEST_FIT, WEAKEST_FIT_DECREASING -> ValueSorterManner.ASCENDING;
      case STRONGEST_FIT, STRONGEST_FIT_DECREASING -> ValueSorterManner.DESCENDING;
      case ALLOCATE_ENTITY_FROM_QUEUE,
          ALLOCATE_TO_VALUE_FROM_QUEUE,
          CHEAPEST_INSERTION,
          ALLOCATE_FROM_POOL ->
          ValueSorterManner.ASCENDING_IF_AVAILABLE;
    };
  }

  /**
   * @return the greedy construction types used by automatic benchmark blueprints, without duplicate
   *     abstract types. {@link #RANDOM_ASSIGNMENT} requires explicit configuration.
   */
  public static ConstructionHeuristicType[] getBluePrintTypes() {
    return new ConstructionHeuristicType[] {
      FIRST_FIT,
      FIRST_FIT_DECREASING,
      WEAKEST_FIT,
      WEAKEST_FIT_DECREASING,
      STRONGEST_FIT,
      STRONGEST_FIT_DECREASING,
      CHEAPEST_INSERTION
    };
  }
}
