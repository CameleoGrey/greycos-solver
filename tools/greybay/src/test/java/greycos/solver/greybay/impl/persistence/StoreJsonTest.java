package greycos.solver.greybay.impl.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class StoreJsonTest {
  @Test
  void canonicalEncodingSortsKeysAndRetainsNullBooleanAndExactStrings() throws Exception {
    var input = new LinkedHashMap<String, Object>();
    input.put("z", null);
    input.put("a", List.of("9223372036854775807", "-0x1.fffffffffffffp1023", true));
    var bytes = StoreJson.canonicalBytes(input);
    assertThat(new String(bytes, StandardCharsets.UTF_8))
        .isEqualTo("{\"a\":[\"9223372036854775807\",\"-0x1.fffffffffffffp1023\",true],\"z\":null}");
    assertThat(StoreJson.readObject(bytes)).isEqualTo(input);
  }

  @Test
  void immutableValuesAreDeepCopies() {
    var list = new ArrayList<Object>(List.of("before"));
    var value = StoreJson.immutableObject(Map.of("values", list));
    list.add("after");
    assertThat(value).containsEntry("values", List.of("before"));
    assertThatThrownBy(() -> value.put("other", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsNumbersObjectsDuplicateKeysAndTrailingDocuments() {
    assertThatThrownBy(() -> StoreJson.canonicalBytes(Map.of("seed", 1L)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("strings");
    assertThatThrownBy(() -> StoreJson.canonicalBytes(Map.of("object", new Object())))
        .isInstanceOf(IllegalArgumentException.class);
    for (var json : List.of("{\"seed\":1}", "{\"a\":\"first\",\"a\":\"second\"}", "{} {}")) {
      assertThatThrownBy(() -> StoreJson.readObject(json.getBytes(StandardCharsets.UTF_8)))
          .isInstanceOf(IOException.class);
    }
  }

  @Test
  void rejectsCyclicPayloadsInsteadOfRecursingForever() {
    var cycle = new LinkedHashMap<String, Object>();
    cycle.put("self", cycle);
    assertThatThrownBy(() -> StoreJson.immutableObject(cycle))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cyclic");
  }
}
