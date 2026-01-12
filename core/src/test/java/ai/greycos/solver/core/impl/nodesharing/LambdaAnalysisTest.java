package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LambdaAnalysisTest {

  @Test
  void hasShareableLambdasEmpty() {
    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);

    assertThat(analysis.hasShareableLambdas()).isFalse();
  }

  @Test
  void hasShareableLambdasNonEmpty() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info1 =
        new LambdaInfo(
            "method",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info2 =
        new LambdaInfo(
            "method",
            1,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info1, info2));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);

    assertThat(analysis.hasShareableLambdas()).isTrue();
  }

  @Test
  void getShareableLambdaGroupCount() {
    LambdaKey key1 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaKey key2 =
        new LambdaKey(
            "java.util.function.Function",
            "com/example/Class.lambda$2",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    LambdaInfo info1 =
        new LambdaInfo(
            "method",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info2 =
        new LambdaInfo(
            "method",
            1,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info3 =
        new LambdaInfo(
            "method",
            2,
            "java.util.function.Function",
            "com/example/Class.lambda$2",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key1, List.of(info1, info2));
    shareableLambdas.put(key2, List.of(info3));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);

    assertThat(analysis.getShareableLambdaGroupCount()).isEqualTo(2);
  }

  @Test
  void getShareableLambdaCount() {
    LambdaKey key1 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaKey key2 =
        new LambdaKey(
            "java.util.function.Function",
            "com/example/Class.lambda$2",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    LambdaInfo info1 =
        new LambdaInfo(
            "method",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info2 =
        new LambdaInfo(
            "method",
            1,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info3 =
        new LambdaInfo(
            "method",
            2,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key1, List.of(info1, info2, info3));
    shareableLambdas.put(key2, List.of());

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);

    assertThat(analysis.getShareableLambdaCount()).isEqualTo(3);
  }

  @Test
  void getAllLambdas() {
    LambdaKey key1 =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaKey key2 =
        new LambdaKey(
            "java.util.function.Function",
            "com/example/Class.lambda$2",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    LambdaInfo info1 =
        new LambdaInfo(
            "method",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info2 =
        new LambdaInfo(
            "method",
            1,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info3 =
        new LambdaInfo(
            "method",
            2,
            "java.util.function.Function",
            "com/example/Class.lambda$2",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key1, List.of(info1, info2));
    shareableLambdas.put(key2, List.of(info3));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);

    assertThat(analysis.getAllLambdas()).hasSize(3);
    assertThat(analysis.getAllLambdas()).containsExactlyInAnyOrder(info1, info2, info3);
  }

  @Test
  void getLambdasForKeyExisting() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info1 =
        new LambdaInfo(
            "method",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info2 =
        new LambdaInfo(
            "method",
            1,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info1, info2));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);

    assertThat(analysis.getLambdasForKey(key)).hasSize(2);
    assertThat(analysis.getLambdasForKey(key)).containsExactly(info1, info2);
  }

  @Test
  void getLambdasForKeyNonExisting() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);

    assertThat(analysis.getLambdasForKey(key)).isEmpty();
  }

  @Test
  void getShareableLambdasReturnsImmutableMap() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info =
        new LambdaInfo(
            "method",
            0,
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);

    assertThatThrownBy(() -> analysis.getShareableLambdas().put(key, List.of()))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
