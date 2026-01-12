package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LambdaDeduplicatorTest {

  @Test
  void predicateFieldName() {
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
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldName(key)).isEqualTo("$predicate1");
  }

  @Test
  void functionFieldName() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Function",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    LambdaInfo info =
        new LambdaInfo(
            "method",
            0,
            "java.util.function.Function",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldName(key)).isEqualTo("$function1");
  }

  @Test
  void biFunctionFieldName() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.BiFunction",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    LambdaInfo info =
        new LambdaInfo(
            "method",
            0,
            "java.util.function.BiFunction",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldName(key)).isEqualTo("$bifunction1");
  }

  @Test
  void triFunctionFieldName() {
    LambdaKey key =
        new LambdaKey(
            "ai.greycos.solver.core.api.score.stream.TriFunction",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    LambdaInfo info =
        new LambdaInfo(
            "method",
            0,
            "ai.greycos.solver.core.api.score.stream.TriFunction",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldName(key)).isEqualTo("$trifunction1");
  }

  @Test
  void quadFunctionFieldName() {
    LambdaKey key =
        new LambdaKey(
            "ai.greycos.solver.core.api.score.stream.QuadFunction",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    LambdaInfo info =
        new LambdaInfo(
            "method",
            0,
            "ai.greycos.solver.core.api.score.stream.QuadFunction",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldName(key)).isEqualTo("$quadfunction1");
  }

  @Test
  void consumerFieldName() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Consumer",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)V",
            List.of());

    LambdaInfo info =
        new LambdaInfo(
            "method",
            0,
            "java.util.function.Consumer",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)V",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldName(key)).isEqualTo("$consumer1");
  }

  @Test
  void biConsumerFieldName() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.BiConsumer",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;Ljava/lang/Object;)V",
            List.of());

    LambdaInfo info =
        new LambdaInfo(
            "method",
            0,
            "java.util.function.BiConsumer",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;Ljava/lang/Object;)V",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldName(key)).isEqualTo("$biconsumer1");
  }

  @Test
  void supplierFieldName() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Supplier",
            "com/example/Class.lambda$1",
            "()Ljava/lang/Object;",
            List.of());

    LambdaInfo info =
        new LambdaInfo(
            "method",
            0,
            "java.util.function.Supplier",
            "com/example/Class.lambda$1",
            "()Ljava/lang/Object;",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldName(key)).isEqualTo("$supplier1");
  }

  @Test
  void joinerFieldName() {
    LambdaKey key =
        new LambdaKey(
            "ai.greycos.solver.core.api.score.stream.Joiner",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info =
        new LambdaInfo(
            "method",
            0,
            "ai.greycos.solver.core.api.score.stream.Joiner",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldName(key)).isEqualTo("$joiner1");
  }

  @Test
  void collectorFieldName() {
    LambdaKey key =
        new LambdaKey(
            "ai.greycos.solver.core.api.score.stream.Collector",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    LambdaInfo info =
        new LambdaInfo(
            "method",
            0,
            "ai.greycos.solver.core.api.score.stream.Collector",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldName(key)).isEqualTo("$collector1");
  }

  @Test
  void unknownFunctionalInterfaceFieldName() {
    LambdaKey key =
        new LambdaKey(
            "com.example.CustomFunctionalInterface",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    LambdaInfo info =
        new LambdaInfo(
            "method",
            0,
            "com.example.CustomFunctionalInterface",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldName(key)).isEqualTo("$lambda1");
  }

  @Test
  void multipleGroupsIncrementIndex() {
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
            "java.util.function.Function",
            "com/example/Class.lambda$2",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key1, List.of(info1));
    shareableLambdas.put(key2, List.of(info2));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    // Check that both keys have different field names
    String fieldName1 = deduplicator.getFieldName(key1);
    String fieldName2 = deduplicator.getFieldName(key2);

    assertThat(fieldName1).isNotNull();
    assertThat(fieldName2).isNotNull();
    assertThat(fieldName1).isNotEqualTo(fieldName2);

    // Check that field names have the correct prefix and different indices
    assertThat(fieldName1).matches("\\$predicate[1-2]");
    assertThat(fieldName2).matches("\\$function[1-2]");

    // Check that indices are different
    int index1 = Integer.parseInt(fieldName1.substring(fieldName1.length() - 1));
    int index2 = Integer.parseInt(fieldName2.substring(fieldName2.length() - 1));
    assertThat(index1).isNotEqualTo(index2);
  }

  @Test
  void fieldDescriptor() {
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
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldDescriptor(key)).isEqualTo("Ljava/util/function/Predicate;");
  }

  @Test
  void fieldDescriptorWithPackage() {
    LambdaKey key =
        new LambdaKey(
            "ai.greycos.solver.core.api.score.stream.TriFunction",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    LambdaInfo info =
        new LambdaInfo(
            "method",
            0,
            "ai.greycos.solver.core.api.score.stream.TriFunction",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    shareableLambdas.put(key, List.of(info));

    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldDescriptor(key))
        .isEqualTo("Lai/greycos/solver/core/api/score/stream/TriFunction;");
  }

  @Test
  void getFieldNameNonExistingKey() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldName(key)).isNull();
  }

  @Test
  void getFieldDescriptorNonExistingKey() {
    LambdaKey key =
        new LambdaKey(
            "java.util.function.Predicate",
            "com/example/Class.lambda$1",
            "(Ljava/lang/Object;)Z",
            List.of());

    Map<LambdaKey, List<LambdaInfo>> shareableLambdas = new HashMap<>();
    LambdaAnalysis analysis = new LambdaAnalysis(shareableLambdas);
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getFieldDescriptor(key)).isNull();
  }

  @Test
  void getAnalysis() {
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
    LambdaDeduplicator deduplicator = new LambdaDeduplicator(analysis);

    assertThat(deduplicator.getAnalysis()).isSameAs(analysis);
  }
}
