package greycos.solver.core.impl.score.stream.bavet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiFunction;
import java.util.function.Function;

import greycos.solver.core.api.function.TriFunction;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class BavetConcatPaddingTest {

  @Test
  void sharingIncludesEveryPaddingFunctionForEveryArityPair() {
    var factory =
        new BavetConstraintStreamImplSupport(ConstraintMatchPolicy.DISABLED)
            .buildConstraintFactory(TestdataSolution.buildSolutionDescriptor());
    var uni = factory.forEach(TestdataEntity.class);
    var bi = uni.join(TestdataEntity.class);
    var tri = bi.join(TestdataEntity.class);
    var quad = tri.join(TestdataEntity.class);
    Function<TestdataEntity, TestdataEntity> uniPadding = a -> a;
    Function<TestdataEntity, TestdataEntity> otherUniPadding = a -> null;
    BiFunction<TestdataEntity, TestdataEntity, TestdataEntity> biPadding = (a, b) -> a;
    BiFunction<TestdataEntity, TestdataEntity, TestdataEntity> otherBiPadding = (a, b) -> null;
    TriFunction<TestdataEntity, TestdataEntity, TestdataEntity, TestdataEntity> triPadding =
        (a, b, c) -> a;
    TriFunction<TestdataEntity, TestdataEntity, TestdataEntity, TestdataEntity> otherTriPadding =
        (a, b, c) -> null;

    assertThat(bi.concat(uni, uniPadding))
        .isSameAs(bi.concat(uni, uniPadding))
        .isNotSameAs(bi.concat(uni, otherUniPadding));
    assertThat(tri.concat(uni, uniPadding, uniPadding))
        .isSameAs(tri.concat(uni, uniPadding, uniPadding))
        .isNotSameAs(tri.concat(uni, otherUniPadding, uniPadding))
        .isNotSameAs(tri.concat(uni, uniPadding, otherUniPadding));
    assertThat(tri.concat(bi, biPadding))
        .isSameAs(tri.concat(bi, biPadding))
        .isNotSameAs(tri.concat(bi, otherBiPadding));
    assertThat(quad.concat(uni, uniPadding, uniPadding, uniPadding))
        .isSameAs(quad.concat(uni, uniPadding, uniPadding, uniPadding))
        .isNotSameAs(quad.concat(uni, otherUniPadding, uniPadding, uniPadding))
        .isNotSameAs(quad.concat(uni, uniPadding, otherUniPadding, uniPadding))
        .isNotSameAs(quad.concat(uni, uniPadding, uniPadding, otherUniPadding));
    assertThat(quad.concat(bi, biPadding, biPadding))
        .isSameAs(quad.concat(bi, biPadding, biPadding))
        .isNotSameAs(quad.concat(bi, otherBiPadding, biPadding))
        .isNotSameAs(quad.concat(bi, biPadding, otherBiPadding));
    assertThat(quad.concat(tri, triPadding))
        .isSameAs(quad.concat(tri, triPadding))
        .isNotSameAs(quad.concat(tri, otherTriPadding));
  }
}
