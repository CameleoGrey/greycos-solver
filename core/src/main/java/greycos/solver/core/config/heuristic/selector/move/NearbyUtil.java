package greycos.solver.core.config.heuristic.selector.move;

import java.util.HashMap;
import java.util.random.RandomGenerator;

import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;

import org.jspecify.annotations.NonNull;

public final class NearbyUtil {

  public static @NonNull ChangeMoveSelectorConfig enable(
      @NonNull ChangeMoveSelectorConfig changeMoveSelectorConfig,
      @NonNull Class<? extends NearbyDistanceMeter<?, ?>> distanceMeter,
      @NonNull RandomGenerator random) {
    var nearbyConfig = changeMoveSelectorConfig.copyConfig();
    makeEntitySelectorIdsUnique(random, nearbyConfig.getEntitySelectorConfig());
    makeValueSelectorIdsUnique(random, nearbyConfig.getValueSelectorConfig());
    var entityConfig = configureEntitySelector(nearbyConfig.getEntitySelectorConfig(), random);
    var valueConfig =
        configureValueSelector(
            nearbyConfig.getValueSelectorConfig(),
            getRecordingSelectorId(entityConfig),
            distanceMeter);
    return nearbyConfig.withEntitySelectorConfig(entityConfig).withValueSelectorConfig(valueConfig);
  }

  private static EntitySelectorConfig configureEntitySelector(
      EntitySelectorConfig entitySelectorConfig, RandomGenerator random) {
    if (entitySelectorConfig == null) {
      entitySelectorConfig = new EntitySelectorConfig();
    }
    if (entitySelectorConfig.getId() == null
        && entitySelectorConfig.getMimicSelectorRef() == null) {
      entitySelectorConfig.withId(ConfigUtils.addRandomSuffix("entitySelector", random));
    }
    return entitySelectorConfig;
  }

  private static String getRecordingSelectorId(EntitySelectorConfig selectorConfig) {
    return selectorConfig.getMimicSelectorRef() == null
        ? selectorConfig.getId()
        : selectorConfig.getMimicSelectorRef();
  }

  private static String getRecordingSelectorId(ValueSelectorConfig selectorConfig) {
    return selectorConfig.getMimicSelectorRef() == null
        ? selectorConfig.getId()
        : selectorConfig.getMimicSelectorRef();
  }

  private static ValueSelectorConfig configureValueSelector(
      ValueSelectorConfig valueSelectorConfig,
      String recordingSelectorId,
      Class<? extends NearbyDistanceMeter<?, ?>> distanceMeter) {
    if (valueSelectorConfig == null) {
      valueSelectorConfig = new ValueSelectorConfig();
    }
    return valueSelectorConfig.withNearbySelectionConfig(
        configureNearbySelectionWithEntity(recordingSelectorId, distanceMeter));
  }

  private static NearbySelectionConfig configureNearbySelectionWithEntity(
      String recordingSelectorId, Class<? extends NearbyDistanceMeter<?, ?>> distanceMeter) {
    return new NearbySelectionConfig()
        .withOriginEntitySelectorConfig(
            new EntitySelectorConfig().withMimicSelectorRef(recordingSelectorId))
        .withNearbyDistanceMeterClass(distanceMeter);
  }

  public static @NonNull ChangeMoveSelectorConfig enable(
      @NonNull ChangeMoveSelectorConfig changeMoveSelectorConfig,
      Class<? extends NearbyDistanceMeter<?, ?>> distanceMeter,
      String recordingSelectorId) {
    var nearbyConfig = changeMoveSelectorConfig.copyConfig();
    var entityConfig = new EntitySelectorConfig().withMimicSelectorRef(recordingSelectorId);
    var valueConfig =
        configureValueSelector(
            nearbyConfig.getValueSelectorConfig(), recordingSelectorId, distanceMeter);
    return nearbyConfig.withEntitySelectorConfig(entityConfig).withValueSelectorConfig(valueConfig);
  }

  public static @NonNull SwapMoveSelectorConfig enable(
      @NonNull SwapMoveSelectorConfig swapMoveSelectorConfig,
      @NonNull Class<? extends NearbyDistanceMeter<?, ?>> distanceMeter,
      @NonNull RandomGenerator random) {
    var nearbyConfig = swapMoveSelectorConfig.copyConfig();
    makeEntitySelectorIdsUnique(
        random,
        nearbyConfig.getEntitySelectorConfig(),
        nearbyConfig.getSecondaryEntitySelectorConfig());
    var entityConfig = configureEntitySelector(nearbyConfig.getEntitySelectorConfig(), random);
    var secondaryConfig = nearbyConfig.getSecondaryEntitySelectorConfig();
    if (secondaryConfig == null) {
      secondaryConfig = new EntitySelectorConfig();
      secondaryConfig.setEntityClass(entityConfig.getEntityClass());
    }
    secondaryConfig.withNearbySelectionConfig(
        configureNearbySelectionWithEntity(getRecordingSelectorId(entityConfig), distanceMeter));
    return nearbyConfig
        .withEntitySelectorConfig(entityConfig)
        .withSecondaryEntitySelectorConfig(secondaryConfig);
  }

  public static @NonNull ListChangeMoveSelectorConfig enable(
      @NonNull ListChangeMoveSelectorConfig listChangeMoveSelectorConfig,
      @NonNull Class<? extends NearbyDistanceMeter<?, ?>> distanceMeter,
      @NonNull RandomGenerator random) {
    var nearbyConfig = listChangeMoveSelectorConfig.copyConfig();
    var destinationConfig = nearbyConfig.getDestinationSelectorConfig();
    if (destinationConfig == null) {
      destinationConfig = new DestinationSelectorConfig();
    }
    makeEntitySelectorIdsUnique(random, destinationConfig.getEntitySelectorConfig());
    makeValueSelectorIdsUnique(
        random, nearbyConfig.getValueSelectorConfig(), destinationConfig.getValueSelectorConfig());
    var valueConfig = configureValueSelector(nearbyConfig.getValueSelectorConfig(), random);
    destinationConfig.withNearbySelectionConfig(
        configureNearbySelectionWithValue(getRecordingSelectorId(valueConfig), distanceMeter));
    nearbyConfig
        .withValueSelectorConfig(valueConfig)
        .withDestinationSelectorConfig(destinationConfig);
    return nearbyConfig;
  }

  private static NearbySelectionConfig configureNearbySelectionWithValue(
      String recordingSelectorId, Class<? extends NearbyDistanceMeter<?, ?>> distanceMeter) {
    return new NearbySelectionConfig()
        .withOriginValueSelectorConfig(
            new ValueSelectorConfig().withMimicSelectorRef(recordingSelectorId))
        .withNearbyDistanceMeterClass(distanceMeter);
  }

  public static @NonNull ListChangeMoveSelectorConfig enable(
      @NonNull ListChangeMoveSelectorConfig listChangeMoveSelectorConfig,
      @NonNull Class<? extends NearbyDistanceMeter<?, ?>> distanceMeter,
      @NonNull String recordingSelectorId) {
    var nearbyConfig = listChangeMoveSelectorConfig.copyConfig();
    var valueConfig = new ValueSelectorConfig().withMimicSelectorRef(recordingSelectorId);
    var destinationConfig = nearbyConfig.getDestinationSelectorConfig();
    if (destinationConfig == null) {
      destinationConfig = new DestinationSelectorConfig();
    }
    destinationConfig.withNearbySelectionConfig(
        configureNearbySelectionWithValue(recordingSelectorId, distanceMeter));
    return nearbyConfig
        .withValueSelectorConfig(valueConfig)
        .withDestinationSelectorConfig(destinationConfig);
  }

  private static ValueSelectorConfig configureValueSelector(
      ValueSelectorConfig valueSelectorConfig, RandomGenerator random) {
    if (valueSelectorConfig == null) {
      valueSelectorConfig = new ValueSelectorConfig();
    }
    if (valueSelectorConfig.getId() == null && valueSelectorConfig.getMimicSelectorRef() == null) {
      valueSelectorConfig.withId(ConfigUtils.addRandomSuffix("valueSelector", random));
    }
    return valueSelectorConfig;
  }

  public static @NonNull ListSwapMoveSelectorConfig enable(
      @NonNull ListSwapMoveSelectorConfig listSwapMoveSelectorConfig,
      @NonNull Class<? extends NearbyDistanceMeter<?, ?>> distanceMeter,
      @NonNull RandomGenerator random) {
    var nearbyConfig = listSwapMoveSelectorConfig.copyConfig();
    makeValueSelectorIdsUnique(
        random,
        nearbyConfig.getValueSelectorConfig(),
        nearbyConfig.getSecondaryValueSelectorConfig());
    var valueConfig = configureValueSelector(nearbyConfig.getValueSelectorConfig(), random);
    var secondaryConfig =
        configureSecondaryValueSelector(
            nearbyConfig.getSecondaryValueSelectorConfig(), valueConfig, distanceMeter);
    return nearbyConfig
        .withValueSelectorConfig(valueConfig)
        .withSecondaryValueSelectorConfig(secondaryConfig);
  }

  private static ValueSelectorConfig configureSecondaryValueSelector(
      ValueSelectorConfig secondaryValueSelectorConfig,
      ValueSelectorConfig primaryValueSelectorConfig,
      Class<? extends NearbyDistanceMeter<?, ?>> distanceMeter) {
    if (secondaryValueSelectorConfig == null) {
      secondaryValueSelectorConfig = new ValueSelectorConfig();
      secondaryValueSelectorConfig.setVariableName(primaryValueSelectorConfig.getVariableName());
    }
    secondaryValueSelectorConfig.withNearbySelectionConfig(
        configureNearbySelectionWithValue(
            getRecordingSelectorId(primaryValueSelectorConfig), distanceMeter));
    return secondaryValueSelectorConfig;
  }

  public static @NonNull KOptListMoveSelectorConfig enable(
      @NonNull KOptListMoveSelectorConfig kOptListMoveSelectorConfig,
      @NonNull Class<? extends NearbyDistanceMeter<?, ?>> distanceMeter,
      @NonNull RandomGenerator random) {
    var nearbyConfig = kOptListMoveSelectorConfig.copyConfig();
    makeValueSelectorIdsUnique(
        random, nearbyConfig.getOriginSelectorConfig(), nearbyConfig.getValueSelectorConfig());
    var originConfig = configureValueSelector(nearbyConfig.getOriginSelectorConfig(), random);
    var valueConfig =
        configureSecondaryValueSelector(
            nearbyConfig.getValueSelectorConfig(), originConfig, distanceMeter);
    return nearbyConfig.withOriginSelectorConfig(originConfig).withValueSelectorConfig(valueConfig);
  }

  // The original move remains in the union. Its copied recorders need different IDs, and
  // references inside the copy must follow the renamed recorders. External references stay intact.
  private static void makeEntitySelectorIdsUnique(
      RandomGenerator random, EntitySelectorConfig... selectorConfigs) {
    var idMap = new HashMap<String, String>();
    for (var selectorConfig : selectorConfigs) {
      if (selectorConfig != null
          && selectorConfig.getId() != null
          && !selectorConfig.getId().isEmpty()) {
        selectorConfig.setId(
            idMap.computeIfAbsent(
                selectorConfig.getId(), id -> ConfigUtils.addRandomSuffix(id, random)));
      }
    }
    for (var selectorConfig : selectorConfigs) {
      if (selectorConfig != null && selectorConfig.getMimicSelectorRef() != null) {
        selectorConfig.setMimicSelectorRef(
            idMap.getOrDefault(
                selectorConfig.getMimicSelectorRef(), selectorConfig.getMimicSelectorRef()));
      }
    }
  }

  private static void makeValueSelectorIdsUnique(
      RandomGenerator random, ValueSelectorConfig... selectorConfigs) {
    var idMap = new HashMap<String, String>();
    for (var selectorConfig : selectorConfigs) {
      if (selectorConfig != null
          && selectorConfig.getId() != null
          && !selectorConfig.getId().isEmpty()) {
        selectorConfig.setId(
            idMap.computeIfAbsent(
                selectorConfig.getId(), id -> ConfigUtils.addRandomSuffix(id, random)));
      }
    }
    for (var selectorConfig : selectorConfigs) {
      if (selectorConfig != null && selectorConfig.getMimicSelectorRef() != null) {
        selectorConfig.setMimicSelectorRef(
            idMap.getOrDefault(
                selectorConfig.getMimicSelectorRef(), selectorConfig.getMimicSelectorRef()));
      }
    }
  }

  private NearbyUtil() {
    // No instances.
  }
}
