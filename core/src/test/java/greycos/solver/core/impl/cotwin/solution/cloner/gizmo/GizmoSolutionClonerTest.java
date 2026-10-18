package greycos.solver.core.impl.cotwin.solution.cloner.gizmo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.solution.cloner.SolutionCloner;
import greycos.solver.core.impl.cotwin.common.ReflectionHelper;
import greycos.solver.core.impl.cotwin.common.accessor.MemberAccessorType;
import greycos.solver.core.impl.cotwin.common.accessor.gizmo.AccessorInfo;
import greycos.solver.core.impl.cotwin.common.accessor.gizmo.GizmoClassLoader;
import greycos.solver.core.impl.cotwin.common.accessor.gizmo.GizmoMemberDescriptor;
import greycos.solver.core.impl.cotwin.solution.cloner.AbstractSolutionClonerTest;
import greycos.solver.core.impl.cotwin.solution.cloner.FieldAccessingSolutionCloner;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.cascade.mixed.TestdataMixedCascadingSolution;
import greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childnot.TestdataOnlyBaseAnnotatedChildEntity;
import greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childnot.TestdataOnlyBaseAnnotatedExtendedSolution;
import greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childnot.TestdataOnlyBaseAnnotatedSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.MethodDesc;

class GizmoSolutionClonerTest extends AbstractSolutionClonerTest {

  @Test
  void varyingCloneSizesKeepIndependentGraphsAndUpdateOnlyAfterSuccess()
      throws ReflectiveOperationException {
    var descriptor = TestdataMixedCascadingSolution.buildSolutionDescriptor();
    var classLoader = new GizmoClassLoader();
    var cloner = GizmoSolutionClonerFactory.build(descriptor, classLoader);
    var otherCloner = GizmoSolutionClonerFactory.build(descriptor, classLoader);
    var countField = cloner.getClass().getDeclaredField("lastCloneObjectCount");
    countField.setAccessible(true);
    assertThat(Modifier.isPrivate(countField.getModifiers())).isTrue();
    assertThat(Modifier.isVolatile(countField.getModifiers())).isTrue();
    assertThat(Modifier.isStatic(countField.getModifiers())).isFalse();
    assertThat(countField.getInt(cloner)).isZero();

    var small = cyclicGraph(2, 1);
    var large = cyclicGraph(8, 24);
    var clonedObjects = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    assertIndependentGraph(small, cloner.cloneSolution(small), clonedObjects);
    var smallCount = countField.getInt(cloner);
    assertThat(smallCount).isPositive();
    assertIndependentGraph(large, cloner.cloneSolution(large), clonedObjects);
    var largeCount = countField.getInt(cloner);
    assertThat(largeCount).isGreaterThan(smallCount);

    assertThatCode(() -> cloner.cloneSolution(new TestdataMixedCascadingSolution() {}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(countField.getInt(cloner)).isEqualTo(largeCount);

    assertIndependentGraph(small, cloner.cloneSolution(small), clonedObjects);
    assertThat(countField.getInt(cloner)).isEqualTo(smallCount);
    assertThat(countField.getInt(otherCloner)).isZero();
    assertIndependentGraph(small, otherCloner.cloneSolution(small), clonedObjects);
    assertThat(countField.getInt(otherCloner)).isEqualTo(smallCount);

    assertThat(cloner.cloneSolution(null)).isNull();
    assertThat(countField.getInt(cloner)).isZero();
    assertIndependentGraph(small, cloner.cloneSolution(small), clonedObjects);
    assertThat(countField.getInt(cloner)).isEqualTo(smallCount);
  }

  @Test
  @Timeout(30)
  void concurrentClonesKeepIdentityMapsIsolated() throws Exception {
    var cloner =
        GizmoSolutionClonerFactory.build(
            TestdataMixedCascadingSolution.buildSolutionDescriptor(), new GizmoClassLoader());
    var originals = List.of(cyclicGraph(2, 1), cyclicGraph(8, 24));
    var ready = new CountDownLatch(4);
    var start = new CountDownLatch(1);
    var futures = new ArrayList<Future<List<TestdataMixedCascadingSolution>>>();
    try (var executor = Executors.newFixedThreadPool(4)) {
      for (var worker = 0; worker < 4; worker++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent clone test did not start.");
                  }
                  var clones = new ArrayList<TestdataMixedCascadingSolution>();
                  for (var iteration = 0; iteration < 8; iteration++) {
                    clones.add(cloner.cloneSolution(originals.get(iteration % originals.size())));
                  }
                  return clones;
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      var clonedObjects = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
      for (var future : futures) {
        var clones = future.get(10, TimeUnit.SECONDS);
        for (var iteration = 0; iteration < clones.size(); iteration++) {
          assertIndependentGraph(
              originals.get(iteration % originals.size()), clones.get(iteration), clonedObjects);
        }
      }
    } finally {
      start.countDown();
    }
  }

  private static TestdataMixedCascadingSolution cyclicGraph(int routeCount, int visitsPerRoute) {
    var solution = TestdataMixedCascadingSolution.generate(routeCount, visitsPerRoute);
    var undeclaredRoute = new UndeclaredRoute("original marker");
    undeclaredRoute.visits = solution.routes.getFirst().visits;
    undeclaredRoute.related = undeclaredRoute;
    undeclaredRoute.other = solution.routes.get(1);
    solution.routes.set(0, undeclaredRoute);
    for (var route : solution.routes) {
      for (var visit : route.visits) {
        visit.route = route;
        visit.partner = solution.visits.getFirst();
      }
    }
    solution.visits.getFirst().dependents.addAll(solution.visits);
    return solution;
  }

  private static void assertIndependentGraph(
      TestdataMixedCascadingSolution original,
      TestdataMixedCascadingSolution clone,
      Set<Object> clonedObjects) {
    assertThat(clone).isNotSameAs(original);
    assertThat(clone.routes).isNotSameAs(original.routes).hasSize(original.routes.size());
    assertThat(clone.visits).isNotSameAs(original.visits).hasSize(original.visits.size());
    assertThat(clone.delays).isSameAs(original.delays);
    assertThat(clonedObjects.add(clone)).isTrue();
    assertThat(clonedObjects.add(clone.routes)).isTrue();
    assertThat(clonedObjects.add(clone.visits)).isTrue();
    var visitIndex = 0;
    for (var routeIndex = 0; routeIndex < clone.routes.size(); routeIndex++) {
      var route = clone.routes.get(routeIndex);
      var originalRoute = original.routes.get(routeIndex);
      assertThat(route).isNotSameAs(originalRoute);
      assertThat(route.visits)
          .isNotSameAs(originalRoute.visits)
          .hasSize(originalRoute.visits.size());
      assertThat(clonedObjects.add(route)).isTrue();
      assertThat(clonedObjects.add(route.visits)).isTrue();
      for (var visit : route.visits) {
        var originalVisit = original.visits.get(visitIndex);
        assertThat(visit).isSameAs(clone.visits.get(visitIndex++)).isNotSameAs(originalVisit);
        assertThat(visit.route).isSameAs(route);
        assertThat(visit.partner).isSameAs(clone.visits.getFirst());
        assertThat(visit.dependents).isNotSameAs(originalVisit.dependents);
        assertThat(clonedObjects.add(visit)).isTrue();
        assertThat(clonedObjects.add(visit.dependents)).isTrue();
      }
    }
    var undeclaredRoute = (UndeclaredRoute) clone.routes.getFirst();
    assertThat(undeclaredRoute.related).isSameAs(undeclaredRoute);
    assertThat(undeclaredRoute.other).isSameAs(clone.routes.get(1));
    assertThat(undeclaredRoute.marker).isEqualTo("original marker");
    assertThat(clone.visits.getFirst().dependents).containsExactlyElementsOf(clone.visits);
  }

  @Test
  void undeclaredEntitySubclassKeepsDeepFieldsAndSharedReferences() {
    var solution = TestdataMixedCascadingSolution.generate(2, 1);
    var route = new UndeclaredRoute();
    route.visits.add(solution.visits.getFirst());
    route.related = route;
    route.other = solution.routes.get(1);
    solution.routes.get(1).visits.getFirst().route = route;
    solution.routes.set(0, route);
    var descriptor = TestdataMixedCascadingSolution.buildSolutionDescriptor();
    var reflectionClone = new FieldAccessingSolutionCloner<>(descriptor).cloneSolution(solution);
    var generatedClone = createSolutionCloner(descriptor).cloneSolution(solution);
    for (var clone : java.util.List.of(reflectionClone, generatedClone)) {
      var clonedRoute = (UndeclaredRoute) clone.routes.getFirst();
      assertThat(clonedRoute).isNotSameAs(route);
      assertThat(clonedRoute.visits).containsExactly(clone.visits.getFirst());
      assertThat(clonedRoute.visits.getFirst()).isNotSameAs(solution.visits.getFirst());
      assertThat(clonedRoute.related).isSameAs(clonedRoute);
      assertThat(clonedRoute.other).isSameAs(clone.routes.get(1));
      assertThat(clone.routes.get(1).visits.getFirst().route).isSameAs(clonedRoute);
      clonedRoute.visits.clear();
      assertThat(route.visits).hasSize(1);
    }
  }

  public static class UndeclaredRoute extends TestdataMixedCascadingSolution.Route {
    private final String marker;
    public TestdataMixedCascadingSolution.Route related;
    public TestdataMixedCascadingSolution.Route other;

    public UndeclaredRoute() {
      this("default marker");
    }

    private UndeclaredRoute(String marker) {
      this.marker = marker;
    }
  }

  @Test
  void debuggingDisabled() {
    assertThat(GizmoSolutionClonerImplementor.DEBUG)
        .as("Gizmo debugging is enabled. Please disable before merging changes.")
        .isFalse();
  }

  @Override
  protected <Solution_> SolutionCloner<Solution_> createSolutionCloner(
      SolutionDescriptor<Solution_> solutionDescriptor) {
    var className = GizmoSolutionClonerFactory.getGeneratedClassName(solutionDescriptor);
    var classBytecodeHolder = new HashMap<String, byte[]>();
    var classOutput =
        GizmoSolutionClonerImplementor.createClassOutputWithDebuggingCapability(
            classBytecodeHolder);

    if (GizmoSolutionClonerImplementor.DEBUG) {
      System.setProperty("gizmo.debug", "true");
    }
    var gizmo = Gizmo.create(classOutput);

    gizmo.class_(
        className,
        classCreator -> {
          classCreator.implements_(GizmoSolutionCloner.class);
          classCreator.extends_(Object.class);
          classCreator.final_();

          var memoizedSolutionOrEntityDescriptorMap =
              new HashMap<Class<?>, GizmoSolutionOrEntityDescriptor>();

          var deepClonedClassSet =
              GizmoCloningUtils.getDeepClonedClasses(solutionDescriptor, Collections.emptyList());
          Stream.concat(
                  Stream.of(solutionDescriptor.getSolutionClass()),
                  Stream.concat(
                      solutionDescriptor.getEntityClassSet().stream(), deepClonedClassSet.stream()))
              .forEach(
                  clazz -> {
                    memoizedSolutionOrEntityDescriptorMap.put(
                        clazz, generateGizmoSolutionOrEntityDescriptor(solutionDescriptor, clazz));
                  });

          GizmoSolutionClonerImplementor.defineClonerFor(
              classCreator,
              solutionDescriptor,
              Collections.singleton(solutionDescriptor.getSolutionClass()),
              memoizedSolutionOrEntityDescriptorMap,
              deepClonedClassSet);
        });

    var gizmoClassLoader = new GizmoClassLoader(classBytecodeHolder);

    try {
      @SuppressWarnings("unchecked")
      var solutionCloner =
          (GizmoSolutionCloner<Solution_>)
              gizmoClassLoader.loadClass(className).getConstructor().newInstance();
      solutionCloner.setSolutionDescriptor(solutionDescriptor);
      return solutionCloner;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed creating generated Gizmo Class (" + className + ").", e);
    }
  }

  // HACK: use public getters/setters of fields so test cotwin can remain private
  // TODO: should this be another CotwinAccessType? CotwinAccessType.GIZMO_RELAXED_ACCESS?
  private GizmoSolutionOrEntityDescriptor generateGizmoSolutionOrEntityDescriptor(
      SolutionDescriptor solutionDescriptor, Class<?> entityClass) {
    var solutionFieldToMemberDescriptor = new HashMap<Field, GizmoMemberDescriptor>();
    var currentClass = entityClass;

    while (currentClass != null) {
      for (var field : currentClass.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers())) {
          GizmoMemberDescriptor member;
          var declaringClass = field.getDeclaringClass();
          var memberDescriptor = FieldDesc.of(field);
          var name = field.getName();

          if (Modifier.isPublic(field.getModifiers())) {
            member =
                new GizmoMemberDescriptor(
                    name,
                    memberDescriptor,
                    declaringClass,
                    AccessorInfo.of(MemberAccessorType.FIELD_OR_READ_METHOD));
          } else {
            var getter = ReflectionHelper.getGetterMethod(currentClass, field.getName());
            var setter =
                ReflectionHelper.getSetterMethod(currentClass, field.getType(), field.getName());
            if (getter != null && setter != null) {
              var getterDescriptor =
                  MethodDesc.of(field.getDeclaringClass(), getter.getName(), field.getType());
              var setterDescriptor =
                  MethodDesc.of(
                      field.getDeclaringClass(),
                      setter.getName(),
                      setter.getReturnType(),
                      field.getType());
              member =
                  new GizmoMemberDescriptor(
                      name,
                      getterDescriptor,
                      memberDescriptor,
                      null,
                      declaringClass,
                      setterDescriptor);
            } else {
              throw new IllegalStateException(
                  """
                                    Failed to generate GizmoMemberDescriptor for (%s#%s):
                                    Field is not public and does not have both a getter and a setter.
                                    """
                      .formatted(declaringClass.getName(), name));
            }
          }
          solutionFieldToMemberDescriptor.put(field, member);
        }
      }
      currentClass = currentClass.getSuperclass();
    }
    return new GizmoSolutionOrEntityDescriptor(
        solutionDescriptor, entityClass, solutionFieldToMemberDescriptor);
  }

  private interface Animal {}

  private interface Robot {}

  private interface Zebra extends Animal {}

  private interface RobotZebra extends Zebra, Robot {}

  // This test verifies the instanceof comparator works correctly
  @Test
  void instanceOfComparatorTest() {
    var classSet =
        new HashSet<>(Arrays.asList(Animal.class, Robot.class, Zebra.class, RobotZebra.class));

    var comparator = GizmoSolutionClonerImplementor.getInstanceOfComparator(classSet);

    // assert that the comparator works on equality
    assertThat(comparator.compare(Animal.class, Animal.class)).isZero();
    assertThat(comparator.compare(Robot.class, Robot.class)).isZero();
    assertThat(comparator.compare(Zebra.class, Zebra.class)).isZero();
    assertThat(comparator.compare(RobotZebra.class, RobotZebra.class)).isZero();

    // Zebra < Animal and Robot
    // Since Animal and Robot are base classes (i.e. not subclasses of anything in the set)
    // and Zebra is a subclass of Animal
    assertThat(comparator.compare(Zebra.class, Animal.class)).isLessThan(0);
    assertThat(comparator.compare(Zebra.class, Robot.class)).isLessThan(0);
    assertThat(comparator.compare(Animal.class, Zebra.class)).isGreaterThan(0);
    assertThat(comparator.compare(Robot.class, Zebra.class)).isGreaterThan(0);

    // RobotZebra < Animal and Robot and Zebra
    assertThat(comparator.compare(RobotZebra.class, Animal.class)).isLessThan(0);
    assertThat(comparator.compare(RobotZebra.class, Robot.class)).isLessThan(0);
    assertThat(comparator.compare(RobotZebra.class, Zebra.class)).isLessThan(0);
    assertThat(comparator.compare(Animal.class, RobotZebra.class)).isGreaterThan(0);
    assertThat(comparator.compare(Robot.class, RobotZebra.class)).isGreaterThan(0);
    assertThat(comparator.compare(Zebra.class, RobotZebra.class)).isGreaterThan(0);
  }

  // This test verifies a proper error message is thrown if an extended solution is passed.
  @Override
  @Test
  protected void cloneExtendedSolution() {
    var solutionDescriptor =
        TestdataOnlyBaseAnnotatedExtendedSolution.buildBaseSolutionDescriptor();
    var cloner = createSolutionCloner(solutionDescriptor);

    var val1 = new TestdataValue("1");
    var val2 = new TestdataValue("2");
    var val3 = new TestdataValue("3");
    var a = new TestdataOnlyBaseAnnotatedChildEntity("a", val1, null);
    var b = new TestdataOnlyBaseAnnotatedChildEntity("b", val1, "extraObjectOnEntity");
    var c = new TestdataOnlyBaseAnnotatedChildEntity("c", val3);
    var d = new TestdataOnlyBaseAnnotatedChildEntity("d", val3, c);
    c.setExtraObject(d);

    var original =
        new TestdataOnlyBaseAnnotatedExtendedSolution("solution", "extraObjectOnSolution");
    var valueList = Arrays.asList(val1, val2, val3);
    original.setValueList(valueList);
    var originalEntityList = Arrays.asList(a, b, c, d);
    original.setEntityList(originalEntityList);

    assertThatCode(() -> cloner.cloneSolution(original))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContainingAll(
            "Failed to create clone: encountered (%s)".formatted(original.getClass()),
            "which is not a known subclass of the solution class (%s)."
                .formatted(TestdataOnlyBaseAnnotatedSolution.class),
            "The known subclasses are: [%s]"
                .formatted(TestdataOnlyBaseAnnotatedSolution.class.getName()),
            "Maybe use CotwinAccessType.REFLECTION?");
  }
}
