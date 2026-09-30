package greycos.solver.core.impl.constructionheuristic.nearby;

import static greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfile.ArgumentShape.ENTITY_ENTITY;
import static greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfile.ArgumentShape.ENTITY_VALUE;
import static greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfile.ArgumentShape.VALUE_DESTINATION;
import static greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfile.ArgumentShape.VALUE_VALUE;
import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySorterManner;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSorterManner;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.score.DummySimpleScoreEasyScoreCalculator;
import greycos.solver.core.impl.solver.random.RandomSource;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar.TestdataAddVarBaseEntity;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar.TestdataAddVarChildEntity;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar.TestdataAddVarSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.mixed.multientity.TestdataMixedMultiEntityFirstEntity;
import greycos.solver.core.testcotwin.mixed.multientity.TestdataMixedMultiEntitySecondEntity;
import greycos.solver.core.testcotwin.mixed.multientity.TestdataMixedMultiEntitySolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarEntity;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarSolution;

import org.junit.jupiter.api.Test;

class ConstructionHeuristicNearbyProfilesTest {

  @Test
  void immutableProfilesDeduplicateByContractAndRetainTraversalOrder() {
    var variable =
        TestdataSolution.buildSolutionDescriptor()
            .getEntityDescriptorStrict(TestdataEntity.class)
            .getGenuineVariableDescriptor("value");
    var first =
        new ConstructionHeuristicNearbyProfile(variable, FirstMeter.class, ENTITY_VALUE, "first");
    var input =
        new ArrayList<>(
            List.of(
                first,
                new ConstructionHeuristicNearbyProfile(
                    variable, SecondMeter.class, ENTITY_VALUE, "second"),
                new ConstructionHeuristicNearbyProfile(
                    variable, FirstMeter.class, ENTITY_VALUE, "duplicate"),
                new ConstructionHeuristicNearbyProfile(
                    variable, FirstMeter.class, ENTITY_ENTITY, "swap")));
    var profiles = new ConstructionHeuristicNearbyProfiles(input);
    input.clear();

    assertThat(profiles.getProfiles(variable))
        .extracting(ConstructionHeuristicNearbyProfile::provenance)
        .containsExactly("first", "second");
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> profiles.getProfiles(variable).clear());
    assertThat(profiles.isEmpty()).isFalse();
    assertThat(ConstructionHeuristicNearbyProfiles.empty().isEmpty()).isTrue();
  }

  @Test
  void directContractReplacesEarlierAnchorContractOnlyForTheSameMeter() {
    var variable =
        TestdataSolution.buildSolutionDescriptor()
            .getEntityDescriptorStrict(TestdataEntity.class)
            .getGenuineVariableDescriptor("value");
    var profiles =
        new ConstructionHeuristicNearbyProfiles(
            List.of(
                new ConstructionHeuristicNearbyProfile(
                    variable, FirstMeter.class, ENTITY_ENTITY, "first-anchor"),
                new ConstructionHeuristicNearbyProfile(
                    variable, SecondMeter.class, ENTITY_ENTITY, "second-anchor"),
                new ConstructionHeuristicNearbyProfile(
                    variable, FirstMeter.class, ENTITY_VALUE, "first-direct"),
                new ConstructionHeuristicNearbyProfile(
                    variable, FirstMeter.class, ENTITY_ENTITY, "duplicate-anchor")));

    assertThat(profiles.getProfiles(variable))
        .extracting(ConstructionHeuristicNearbyProfile::provenance)
        .containsExactly("second-anchor", "first-direct");
  }

  @Test
  void nearestFollowingSearchIsResolvedSeparatelyForEachVariable() {
    var descriptor = TestdataMultiVarSolution.buildSolutionDescriptor();
    var entity = descriptor.getEntityDescriptorStrict(TestdataMultiVarEntity.class);
    var primary = entity.getGenuineVariableDescriptor("primaryValue");
    var secondary = entity.getGenuineVariableDescriptor("secondaryValue");
    var tertiary = entity.getGenuineVariableDescriptor("tertiaryValueAllowedUnassigned");
    var phases =
        List.<PhaseConfig>of(
            new ConstructionHeuristicPhaseConfig(),
            new AlnsPhaseConfig(),
            new CustomPhaseConfig(),
            search(change("primaryValue", FirstMeter.class)),
            search(change("primaryValue", SecondMeter.class)),
            search(change("secondaryValue", SecondMeter.class)));

    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveForPhase(
            phases, 0, buildHeuristicConfigPolicy(descriptor));

    assertThat(profiles.getProfiles(primary))
        .extracting(ConstructionHeuristicNearbyProfile::distanceMeterClass)
        .containsExactly(FirstMeter.class);
    assertThat(profiles.getProfiles(secondary))
        .extracting(ConstructionHeuristicNearbyProfile::distanceMeterClass)
        .containsExactly(SecondMeter.class);
    assertThat(profiles.getProfiles(tertiary)).isEmpty();
    assertThat(profiles.getProfiles(primary).getFirst().provenance()).contains("phase[3]");
  }

  @Test
  void effectiveIslandChildrenOverrideConvenienceSelector() {
    var descriptor = TestdataSolution.buildSolutionDescriptor();
    var variable =
        descriptor
            .getEntityDescriptorStrict(TestdataEntity.class)
            .getGenuineVariableDescriptor("value");
    var island =
        new IslandModelPhaseConfig()
            .withMoveSelectorConfig(change("value", FirstMeter.class))
            .withPhaseConfigList(
                List.of(new AlnsPhaseConfig(), search(change("value", SecondMeter.class))));

    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveForPhase(
            List.of(new ConstructionHeuristicPhaseConfig(), island),
            0,
            buildHeuristicConfigPolicy(descriptor));

    assertThat(profiles.getProfiles(variable))
        .extracting(ConstructionHeuristicNearbyProfile::distanceMeterClass)
        .containsExactly(SecondMeter.class);
    assertThat(profiles.getProfiles(variable).getFirst().provenance())
        .contains("islandModel.phase[1]");
  }

  @Test
  void traversesNestedPartitionAndIslandShorthand() {
    var descriptor = TestdataSolution.buildSolutionDescriptor();
    var variable =
        descriptor
            .getEntityDescriptorStrict(TestdataEntity.class)
            .getGenuineVariableDescriptor("value");
    var island =
        new IslandModelPhaseConfig().withMoveSelectorConfig(change("value", FirstMeter.class));
    var partition =
        new PartitionedSearchPhaseConfig().withPhaseConfigs(new CustomPhaseConfig(), island);

    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveForPhase(
            List.of(new ConstructionHeuristicPhaseConfig(), partition),
            0,
            buildHeuristicConfigPolicy(descriptor));

    assertThat(profiles.getProfiles(variable))
        .extracting(ConstructionHeuristicNearbyProfile::distanceMeterClass)
        .containsExactly(FirstMeter.class);
    assertThat(profiles.getProfiles(variable).getFirst().provenance())
        .contains("partitionedSearch.phase[1].islandModel.localSearch");
  }

  @Test
  void globalStandaloneConstructionAndDefaultPartitionUseDefaultContracts() {
    var descriptor = TestdataSolution.buildSolutionDescriptor();
    var variable =
        descriptor
            .getEntityDescriptorStrict(TestdataEntity.class)
            .getGenuineVariableDescriptor("value");
    var global =
        ConstructionHeuristicNearbyProfileResolver.resolveGlobal(descriptor, FirstMeter.class);
    var policy =
        buildHeuristicConfigPolicy(descriptor)
            .cloneBuilder()
            .withNearbyDistanceMeterClass(FirstMeter.class)
            .withConstructionHeuristicNearbyProfiles(global)
            .build();

    assertThat(
            ConstructionHeuristicNearbyProfileResolver.resolveForPhase(
                    List.of(new ConstructionHeuristicPhaseConfig()), 0, policy)
                .getProfiles(variable))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(ENTITY_VALUE);
    assertThat(
            ConstructionHeuristicNearbyProfileResolver.resolveForPhase(
                    List.of(
                        new ConstructionHeuristicPhaseConfig(), new PartitionedSearchPhaseConfig()),
                    0,
                    policy)
                .getProfiles(variable))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(ENTITY_VALUE);
  }

  @Test
  void mixedGlobalProfilesRemainListOnly() {
    var descriptor = TestdataMixedSolution.buildSolutionDescriptor();
    var entity = descriptor.getEntityDescriptorStrict(TestdataMixedEntity.class);
    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveGlobal(descriptor, FirstMeter.class);

    assertThat(profiles.getProfiles(entity.getGenuineVariableDescriptor("valueList")))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(VALUE_DESTINATION);
    assertThat(profiles.getProfiles(entity.getGenuineVariableDescriptor("basicValue"))).isEmpty();
    assertThat(profiles.getProfiles(entity.getGenuineVariableDescriptor("secondBasicValue")))
        .isEmpty();
  }

  @Test
  void genericChangeAndSwapNormalizeToListContracts() {
    var descriptor = TestdataListSolution.buildSolutionDescriptor();
    var variable =
        descriptor
            .getEntityDescriptorStrict(TestdataListEntity.class)
            .getGenuineVariableDescriptor("valueList");
    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveLocalSearch(
            search(
                new UnionMoveSelectorConfig()
                    .withMoveSelectors(
                        new ChangeMoveSelectorConfig(), new SwapMoveSelectorConfig())),
            descriptor,
            FirstMeter.class,
            "search");

    assertThat(profiles.getProfiles(variable))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(VALUE_DESTINATION);
  }

  @Test
  void globalContractsAreRestrictedByConcreteMeterParameters() {
    var basicDescriptor = TestdataSolution.buildSolutionDescriptor();
    var basicVariable =
        basicDescriptor
            .getEntityDescriptorStrict(TestdataEntity.class)
            .getGenuineVariableDescriptor("value");
    assertThat(
            ConstructionHeuristicNearbyProfileResolver.resolveGlobal(
                    basicDescriptor, BasicMeter.class)
                .getProfiles(basicVariable))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(ENTITY_VALUE);
    assertThat(
            ConstructionHeuristicNearbyProfileResolver.resolveGlobal(
                    basicDescriptor, SwapMeter.class)
                .getProfiles(basicVariable))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(ENTITY_ENTITY);

    var listDescriptor = TestdataListSolution.buildSolutionDescriptor();
    var listVariable =
        listDescriptor
            .getEntityDescriptorStrict(TestdataListEntity.class)
            .getGenuineVariableDescriptor("valueList");
    assertThat(
            ConstructionHeuristicNearbyProfileResolver.resolveGlobal(
                    listDescriptor, ListValueMeter.class)
                .getProfiles(listVariable))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(VALUE_VALUE);

    var multiDescriptor = TestdataMultiVarSolution.buildSolutionDescriptor();
    var multiEntity = multiDescriptor.getEntityDescriptorStrict(TestdataMultiVarEntity.class);
    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveGlobal(
            multiDescriptor, MultiVariableMeter.class);
    assertThat(profiles.getProfiles(multiEntity.getGenuineVariableDescriptor("primaryValue")))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(ENTITY_VALUE);
    assertThat(profiles.getProfiles(multiEntity.getGenuineVariableDescriptor("secondaryValue")))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(ENTITY_VALUE);
    assertThat(
            profiles.getProfiles(
                multiEntity.getGenuineVariableDescriptor("tertiaryValueAllowedUnassigned")))
        .isEmpty();
  }

  @Test
  void manualSwapUsesOnlyIncludedBasicVariablesAndEntityArgumentContract() {
    var descriptor = TestdataMultiVarSolution.buildSolutionDescriptor();
    var entity = descriptor.getEntityDescriptorStrict(TestdataMultiVarEntity.class);
    var swap =
        new SwapMoveSelectorConfig()
            .withVariableNameIncludes("secondaryValue")
            .withEntitySelectorConfig(new EntitySelectorConfig().withId("entity"))
            .withSecondaryEntitySelectorConfig(
                new EntitySelectorConfig()
                    .withNearbySelectionConfig(entityNearby(FirstMeter.class)));

    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveLocalSearch(
            search(swap), descriptor, null, "search");

    assertThat(profiles.getProfiles(entity.getGenuineVariableDescriptor("secondaryValue")))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(ENTITY_ENTITY);
    assertThat(profiles.getProfiles(entity.getGenuineVariableDescriptor("primaryValue"))).isEmpty();
  }

  @Test
  void manualListFamiliesRetainDestinationAndValueArgumentContracts() {
    var descriptor = TestdataListSolution.buildSolutionDescriptor();
    var variable =
        descriptor
            .getEntityDescriptorStrict(TestdataListEntity.class)
            .getGenuineVariableDescriptor("valueList");
    var source = new ValueSelectorConfig().withId("value").withVariableName("valueList");
    var nearby = valueNearby(FirstMeter.class);
    var moves =
        new UnionMoveSelectorConfig()
            .withMoveSelectors(
                new ListChangeMoveSelectorConfig()
                    .withValueSelectorConfig(source.copyConfig())
                    .withDestinationSelectorConfig(
                        new DestinationSelectorConfig()
                            .withNearbySelectionConfig(nearby.copyConfig())),
                new ListSwapMoveSelectorConfig()
                    .withValueSelectorConfig(source.copyConfig())
                    .withSecondaryValueSelectorConfig(
                        new ValueSelectorConfig().withNearbySelectionConfig(nearby.copyConfig())),
                new KOptListMoveSelectorConfig()
                    .withOriginSelectorConfig(source.copyConfig())
                    .withValueSelectorConfig(
                        new ValueSelectorConfig()
                            .withNearbySelectionConfig(valueNearby(SecondMeter.class))),
                new SubListChangeMoveSelectorConfig()
                    .withSubListSelectorConfig(
                        new SubListSelectorConfig()
                            .withValueSelectorConfig(source.copyConfig())
                            .withId("sublist"))
                    .withDestinationSelectorConfig(
                        new DestinationSelectorConfig()
                            .withNearbySelectionConfig(
                                new NearbySelectionConfig()
                                    .withOriginSubListSelectorConfig(
                                        new SubListSelectorConfig().withMimicSelectorRef("sublist"))
                                    .withNearbyDistanceMeterClass(ThirdMeter.class))),
                new SubListSwapMoveSelectorConfig()
                    .withSubListSelectorConfig(
                        new SubListSelectorConfig()
                            .withId("sublist")
                            .withValueSelectorConfig(source.copyConfig()))
                    .withSecondarySubListSelectorConfig(
                        new SubListSelectorConfig()
                            .withNearbySelectionConfig(
                                new NearbySelectionConfig()
                                    .withOriginSubListSelectorConfig(
                                        new SubListSelectorConfig().withMimicSelectorRef("sublist"))
                                    .withNearbyDistanceMeterClass(SecondMeter.class))));

    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveLocalSearch(
            search(moves), descriptor, null, "search");

    assertThat(profiles.getProfiles(variable))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(VALUE_DESTINATION, VALUE_VALUE, VALUE_DESTINATION);
    assertThat(profiles.getProfiles(variable))
        .extracting(ConstructionHeuristicNearbyProfile::distanceMeterClass)
        .containsExactly(FirstMeter.class, SecondMeter.class, ThirdMeter.class);
  }

  @Test
  void mimicRecordersResolveAcrossCompositeChildrenWithoutMutationOrRandomUse() {
    var descriptor = TestdataMixedMultiEntitySolution.buildSolutionDescriptor();
    var list =
        descriptor
            .getEntityDescriptorStrict(TestdataMixedMultiEntityFirstEntity.class)
            .getGenuineVariableDescriptor("valueList");
    var basic =
        descriptor
            .getEntityDescriptorStrict(TestdataMixedMultiEntitySecondEntity.class)
            .getGenuineVariableDescriptor("basicValue");
    var scalarRecorder =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig()
                    .withId("entity")
                    .withEntityClass(TestdataMixedMultiEntitySecondEntity.class));
    var scalarReplay =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withMimicSelectorRef("entity"))
            .withValueSelectorConfig(
                new ValueSelectorConfig()
                    .withVariableName("basicValue")
                    .withNearbySelectionConfig(entityNearby(FirstMeter.class)));
    var listRecorder =
        new ListChangeMoveSelectorConfig()
            .withValueSelectorConfig(
                new ValueSelectorConfig().withId("value").withVariableName("valueList"));
    var listReplay =
        new ListSwapMoveSelectorConfig()
            .withValueSelectorConfig(new ValueSelectorConfig().withMimicSelectorRef("value"))
            .withSecondaryValueSelectorConfig(
                new ValueSelectorConfig()
                    .withNearbySelectionConfig(valueNearby(SecondMeter.class)));
    var selector =
        new CartesianProductMoveSelectorConfig()
            .withMoveSelectors(
                scalarRecorder,
                scalarReplay,
                new UnionMoveSelectorConfig().withMoveSelectors(listRecorder, listReplay));
    var phase = search(selector);
    var snapshot = phase.copyConfig();
    var random = mock(RandomSource.class);
    var policy = buildHeuristicConfigPolicy(descriptor).cloneBuilder().withRandom(random).build();

    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveForPhase(
            List.of(new ConstructionHeuristicPhaseConfig(), phase), 0, policy);

    assertThat(profiles.getProfiles(basic))
        .extracting(ConstructionHeuristicNearbyProfile::distanceMeterClass)
        .containsExactly(FirstMeter.class);
    assertThat(profiles.getProfiles(list))
        .extracting(ConstructionHeuristicNearbyProfile::distanceMeterClass)
        .containsExactly(SecondMeter.class);
    assertThat(phase).usingRecursiveComparison().isEqualTo(snapshot);
    verifyNoInteractions(random);
  }

  @Test
  void enclosingLocalSearchOwnProfilesReplaceInheritedProfilesForRuinRecreate() {
    var descriptor = TestdataSolution.buildSolutionDescriptor();
    var variable =
        descriptor
            .getEntityDescriptorStrict(TestdataEntity.class)
            .getGenuineVariableDescriptor("value");
    var inherited =
        ConstructionHeuristicNearbyProfileResolver.resolveGlobal(descriptor, FirstMeter.class);
    var policy =
        buildHeuristicConfigPolicy(descriptor)
            .cloneBuilder()
            .withConstructionHeuristicNearbyProfiles(inherited)
            .build();

    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveForPhase(
            List.of(
                search(change("value", SecondMeter.class)),
                search(change("value", FirstMeter.class))),
            0,
            policy);

    assertThat(profiles.getProfiles(variable))
        .extracting(ConstructionHeuristicNearbyProfile::distanceMeterClass)
        .containsExactly(SecondMeter.class);
  }

  @Test
  void policyCopiesShareMetadataAndPreserveConstructionSettings() {
    var descriptor = TestdataSolution.buildSolutionDescriptor();
    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveGlobal(descriptor, FirstMeter.class);
    var policy =
        buildHeuristicConfigPolicy(descriptor)
            .cloneBuilder()
            .withNearbyDistanceMeterClass(FirstMeter.class)
            .withConstructionHeuristicNearbyProfiles(profiles)
            .withConstructionHeuristicNearbyAutoConfigurationEnabled(false)
            .withConstructionHeuristicNearbySelectionSize(7)
            .withEntitySorterManner(EntitySorterManner.DESCENDING)
            .withValueSorterManner(ValueSorterManner.ASCENDING)
            .withReinitializeVariableFilterEnabled(true)
            .withUnassignedValuesAllowed(true)
            .build();

    for (var copy :
        List.of(
            policy.copyConfigPolicy(),
            policy.copyPhaseConfigPolicy(),
            policy.copyChildThreadConfigPolicy(),
            policy.copyConfigPolicyWithoutNearbySetting())) {
      assertThat(copy.getConstructionHeuristicNearbyProfiles()).isSameAs(profiles);
      assertThat(copy.isConstructionHeuristicNearbyAutoConfigurationEnabled()).isFalse();
      assertThat(copy.getConstructionHeuristicNearbySelectionSize()).isEqualTo(7);
    }
    var withoutNearby = policy.copyConfigPolicyWithoutNearbySetting();
    assertThat(withoutNearby.getNearbyDistanceMeterClass()).isNull();
    assertThat(withoutNearby.getEntitySorterManner()).isEqualTo(EntitySorterManner.DESCENDING);
    assertThat(withoutNearby.getValueSorterManner()).isEqualTo(ValueSorterManner.ASCENDING);
    assertThat(withoutNearby.isReinitializeVariableFilterEnabled()).isTrue();
    assertThat(withoutNearby.isUnassignedValuesAllowed()).isTrue();
  }

  @Test
  void parentRecorderAndExplicitDowncastResolveOnlyTheChildVariable() {
    var descriptor = inheritanceDescriptor();
    var child = descriptor.getEntityDescriptorStrict(TestdataAddVarChildEntity.class);
    var sibling = descriptor.getEntityDescriptorStrict(SiblingEntity.class);
    var recorder =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig(TestdataAddVarBaseEntity.class).withId("entity"))
            .withValueSelectorConfig(new ValueSelectorConfig("value"));
    var downcast =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withMimicSelectorRef("entity"))
            .withValueSelectorConfig(
                new ValueSelectorConfig("value2")
                    .withDowncastEntityClass(TestdataAddVarChildEntity.class)
                    .withNearbySelectionConfig(entityNearby(FirstMeter.class)));

    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveLocalSearch(
            search(new UnionMoveSelectorConfig().withMoveSelectors(recorder, downcast)),
            descriptor,
            null,
            "search");

    assertThat(profiles.getProfiles(child.getGenuineVariableDescriptor("value2")))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(ENTITY_VALUE);
    assertThat(profiles.getProfiles(child.getGenuineVariableDescriptor("value"))).isEmpty();
    assertThat(profiles.getProfiles(sibling.getGenuineVariableDescriptor("value2"))).isEmpty();
  }

  @Test
  void childContextRetainsInheritedVariableIdentityAndAcceptsParentOrigin() {
    var descriptor = inheritanceDescriptor();
    var base = descriptor.getEntityDescriptorStrict(TestdataAddVarBaseEntity.class);
    var child = descriptor.getEntityDescriptorStrict(TestdataAddVarChildEntity.class);
    assertThat(child.getGenuineVariableDescriptor("value"))
        .isSameAs(base.getGenuineVariableDescriptor("value"));
    var change =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig(TestdataAddVarBaseEntity.class).withId("entity"))
            .withValueSelectorConfig(
                new ValueSelectorConfig("value")
                    .withDowncastEntityClass(TestdataAddVarChildEntity.class)
                    .withNearbySelectionConfig(entityNearby(FirstMeter.class)));

    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveLocalSearch(
            search(change), descriptor, null, "search");

    assertThat(profiles.getProfiles(base.getGenuineVariableDescriptor("value")))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(ENTITY_VALUE);
    assertThat(profiles.getProfiles(child.getGenuineVariableDescriptor("value2"))).isEmpty();
  }

  @Test
  void siblingEntityOriginDoesNotDescribeAChildAssignment() {
    var descriptor = inheritanceDescriptor();
    var recorder =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig(SiblingEntity.class).withId("entity"))
            .withValueSelectorConfig(new ValueSelectorConfig("value2"));
    var childChange =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig(TestdataAddVarChildEntity.class))
            .withValueSelectorConfig(
                new ValueSelectorConfig("value2")
                    .withNearbySelectionConfig(entityNearby(FirstMeter.class)));

    assertThat(
            ConstructionHeuristicNearbyProfileResolver.resolveLocalSearch(
                    search(new UnionMoveSelectorConfig().withMoveSelectors(recorder, childChange)),
                    descriptor,
                    null,
                    "search")
                .isEmpty())
        .isTrue();
  }

  @Test
  void valueMimicWithExplicitNameUsesRecordedDescriptorInInheritedContext() {
    var descriptor = inheritanceDescriptor();
    var profiles = resolveValueReplay(descriptor, TestdataAddVarChildEntity.class, "value");
    var base = descriptor.getEntityDescriptorStrict(TestdataAddVarBaseEntity.class);

    assertThat(profiles.getProfiles(base.getGenuineVariableDescriptor("value")))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(ENTITY_ENTITY);
    assertThat(
            profiles.getProfiles(
                descriptor
                    .getEntityDescriptorStrict(TestdataAddVarChildEntity.class)
                    .getGenuineVariableDescriptor("value2")))
        .isEmpty();
  }

  @Test
  void valueMimicRejectsMismatchedNameAndUnrelatedSameNameDescriptor() {
    var descriptor = inheritanceDescriptor();
    assertThat(resolveValueReplay(descriptor, TestdataAddVarChildEntity.class, "value2").isEmpty())
        .isTrue();
    assertThat(resolveValueReplay(descriptor, UnrelatedEntity.class, "value").isEmpty()).isTrue();
  }

  @Test
  void valueMimicRejectsSiblingSameNameDescriptorEvenWithCompatibleParentOrigin() {
    var descriptor = inheritanceDescriptor();
    var parentOrigin =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig(TestdataAddVarBaseEntity.class).withId("entity"))
            .withValueSelectorConfig(new ValueSelectorConfig("value"));
    var valueRecorder =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig(TestdataAddVarChildEntity.class))
            .withValueSelectorConfig(new ValueSelectorConfig("value2").withId("recordedValue"));
    var siblingReplay =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig(SiblingEntity.class)
                    .withNearbySelectionConfig(entityNearby(FirstMeter.class)))
            .withValueSelectorConfig(
                new ValueSelectorConfig("value2").withMimicSelectorRef("recordedValue"));

    assertThat(
            ConstructionHeuristicNearbyProfileResolver.resolveLocalSearch(
                    search(
                        new UnionMoveSelectorConfig()
                            .withMoveSelectors(parentOrigin, valueRecorder, siblingReplay)),
                    descriptor,
                    null,
                    "search")
                .isEmpty())
        .isTrue();
  }

  @Test
  void registeredShadowOnlyParentSupportsDowncastToGenuineChild() {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            ShadowParentSolution.class, ShadowParent.class, GenuineChild.class);
    var parent = descriptor.getEntityDescriptorStrict(ShadowParent.class);
    var child = descriptor.getEntityDescriptorStrict(GenuineChild.class);
    assertThat(parent.isGenuine()).isFalse();
    var change =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig(ShadowParent.class).withId("entity"))
            .withValueSelectorConfig(
                new ValueSelectorConfig("value")
                    .withDowncastEntityClass(GenuineChild.class)
                    .withNearbySelectionConfig(entityNearby(FirstMeter.class)));

    var profiles =
        ConstructionHeuristicNearbyProfileResolver.resolveLocalSearch(
            search(change), descriptor, null, "search");

    assertThat(profiles.getProfiles(child.getGenuineVariableDescriptor("value")))
        .extracting(ConstructionHeuristicNearbyProfile::argumentShape)
        .containsExactly(ENTITY_VALUE);
    var config =
        new SolverConfig()
            .withSolutionClass(ShadowParentSolution.class)
            .withEntityClasses(ShadowParent.class, GenuineChild.class)
            .withEasyScoreCalculatorClass(DummySimpleScoreEasyScoreCalculator.class)
            .withPhases(
                new ConstructionHeuristicPhaseConfig(),
                search(change)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(0)));
    assertThat(SolverFactory.create(config).buildSolver()).isNotNull();
  }

  @Test
  void missingRecordersDoNotCreateBroadProfiles() {
    var descriptor = TestdataSolution.buildSolutionDescriptor();
    var missingEntity =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig().withMimicSelectorRef("entity"))
            .withValueSelectorConfig(
                new ValueSelectorConfig("value")
                    .withNearbySelectionConfig(entityNearby(FirstMeter.class)));
    var missingValue =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig(TestdataEntity.class)
                    .withId("entity")
                    .withNearbySelectionConfig(entityNearby(FirstMeter.class)))
            .withValueSelectorConfig(
                new ValueSelectorConfig("value").withMimicSelectorRef("missingValue"));

    for (var change : List.of(missingEntity, missingValue)) {
      assertThat(
              ConstructionHeuristicNearbyProfileResolver.resolveLocalSearch(
                      search(change), descriptor, null, "search")
                  .isEmpty())
          .isTrue();
    }
  }

  private static SolutionDescriptor<TestdataAddVarSolution> inheritanceDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataAddVarSolution.class,
        TestdataAddVarBaseEntity.class,
        TestdataAddVarChildEntity.class,
        SiblingEntity.class,
        UnrelatedEntity.class);
  }

  private static ConstructionHeuristicNearbyProfiles resolveValueReplay(
      SolutionDescriptor<?> descriptor, Class<?> replayEntity, String replayName) {
    var recorder =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig(TestdataAddVarBaseEntity.class).withId("entity"))
            .withValueSelectorConfig(new ValueSelectorConfig("value").withId("recordedValue"));
    var replay =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig(replayEntity)
                    .withNearbySelectionConfig(entityNearby(FirstMeter.class)))
            .withValueSelectorConfig(
                new ValueSelectorConfig(replayName).withMimicSelectorRef("recordedValue"));
    return ConstructionHeuristicNearbyProfileResolver.resolveLocalSearch(
        search(new UnionMoveSelectorConfig().withMoveSelectors(recorder, replay)),
        descriptor,
        null,
        "search");
  }

  @PlanningEntity
  public static class SiblingEntity extends TestdataAddVarBaseEntity {
    @PlanningVariable(valueRangeProviderRefs = "valueRange2")
    public String value2;
  }

  @PlanningEntity
  public static class UnrelatedEntity {
    @PlanningVariable(valueRangeProviderRefs = "valueRange")
    public String value;
  }

  @PlanningEntity
  public static class ShadowParent {
    @InverseRelationShadowVariable(sourceVariableName = "value")
    public List<GenuineChild> children;
  }

  @PlanningEntity
  public static class GenuineChild extends ShadowParent {
    @PlanningVariable(valueRangeProviderRefs = "valueRange")
    public ShadowParent value;
  }

  @PlanningSolution
  public static class ShadowParentSolution {
    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "valueRange")
    public List<ShadowParent> values;

    @PlanningEntityCollectionProperty public List<GenuineChild> entities;
    @PlanningScore public SimpleScore score;
  }

  private static LocalSearchPhaseConfig search(
      greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig<?> selector) {
    return new LocalSearchPhaseConfig().withMoveSelectorConfig(selector);
  }

  private static ChangeMoveSelectorConfig change(
      String variableName, Class<? extends NearbyDistanceMeter<?, ?>> meter) {
    return new ChangeMoveSelectorConfig()
        .withEntitySelectorConfig(new EntitySelectorConfig().withId("entity"))
        .withValueSelectorConfig(
            new ValueSelectorConfig()
                .withVariableName(variableName)
                .withNearbySelectionConfig(entityNearby(meter)));
  }

  private static NearbySelectionConfig entityNearby(
      Class<? extends NearbyDistanceMeter<?, ?>> meter) {
    return new NearbySelectionConfig()
        .withOriginEntitySelectorConfig(new EntitySelectorConfig().withMimicSelectorRef("entity"))
        .withNearbyDistanceMeterClass(meter);
  }

  private static NearbySelectionConfig valueNearby(
      Class<? extends NearbyDistanceMeter<?, ?>> meter) {
    return new NearbySelectionConfig()
        .withOriginValueSelectorConfig(new ValueSelectorConfig().withMimicSelectorRef("value"))
        .withNearbyDistanceMeterClass(meter);
  }

  public static class FirstMeter implements NearbyDistanceMeter<Object, Object> {
    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      throw new AssertionError("Metadata discovery must not call distance meters.");
    }
  }

  public static class SecondMeter extends FirstMeter {}

  public static class ThirdMeter extends FirstMeter {}

  public static class BasicMeter implements NearbyDistanceMeter<TestdataEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(TestdataEntity origin, TestdataValue destination) {
      throw new AssertionError("Metadata discovery must not call distance meters.");
    }
  }

  public static class SwapMeter implements NearbyDistanceMeter<TestdataEntity, TestdataEntity> {
    @Override
    public double getNearbyDistance(TestdataEntity origin, TestdataEntity destination) {
      throw new AssertionError("Metadata discovery must not call distance meters.");
    }
  }

  public static class ListValueMeter
      implements NearbyDistanceMeter<TestdataListValue, TestdataListValue> {
    @Override
    public double getNearbyDistance(TestdataListValue origin, TestdataListValue destination) {
      throw new AssertionError("Metadata discovery must not call distance meters.");
    }
  }

  public static class MultiVariableMeter
      implements NearbyDistanceMeter<TestdataMultiVarEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(TestdataMultiVarEntity origin, TestdataValue destination) {
      throw new AssertionError("Metadata discovery must not call distance meters.");
    }

    public double getNearbyDistance(String origin, String destination) {
      throw new AssertionError("An unrelated overload must not broaden the meter contract.");
    }
  }
}
