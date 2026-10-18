package greycos.solver.core.impl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class ShrinkingIdentityHashMapTest {

  @Test
  void clearSmallMapInPlace() {
    var map = new ShrinkingIdentityHashMap<Integer, Integer>();
    map.put(1, 1);
    var delegate = map.delegate();
    map.clear();
    assertThat(map.delegate()).isSameAs(delegate);
    assertThat(map.isEmpty()).isTrue();
  }

  @Test
  void clearLargeMapInPlaceWhenNearMax() {
    var map = new ShrinkingIdentityHashMap<Integer, Integer>();
    putAll(map, 100);
    var delegate = map.delegate();
    map.clear();
    assertThat(map.delegate()).isSameAs(delegate);
    assertThat(map.isEmpty()).isTrue();
  }

  @Test
  void replaceLargeMapWhenFarBelowMax() {
    var map = new ShrinkingIdentityHashMap<Integer, Integer>();
    putAll(map, 1000);
    var delegate = map.delegate();
    map.clear();
    assertThat(map.delegate()).isSameAs(delegate);
    Integer key = 1;
    for (var cycle = 0; cycle < 10; cycle++) {
      map.put(key, key);
      map.clear();
      if (map.delegate() != delegate) {
        assertThat(map.isEmpty()).isTrue();
        map.put(key, key);
        assertThat(map.get(key)).isSameAs(key);
        return;
      }
    }
    fail("The map was not replaced within 10 clears.");
  }

  @Test
  void keepEmptyMapEvenWhenFarBelowMax() {
    var map = new ShrinkingIdentityHashMap<Integer, Integer>();
    putAll(map, 1000);
    map.clear();
    var delegate = map.delegate();
    for (var cycle = 0; cycle < 20; cycle++) {
      map.clear();
    }
    assertThat(map.delegate()).isSameAs(delegate);
    assertThat(map.isEmpty()).isTrue();
  }

  @Test
  void peakSurvivesRemove() {
    var map = new ShrinkingIdentityHashMap<Object, Object>();
    var keys = new Object[1000];
    for (var i = 0; i < keys.length; i++) {
      keys[i] = new Object();
      map.put(keys[i], keys[i]);
    }
    for (var i = 1; i < keys.length; i++) {
      map.remove(keys[i]);
    }
    var delegate = map.delegate();
    map.clear();
    assertThat(map.delegate()).isSameAs(delegate);
  }

  @Test
  void equalKeysRemainDistinctAfterShrinking() {
    record Key(int id) {}
    var map = new ShrinkingIdentityHashMap<Key, Object>();
    for (var i = 0; i < 1000; i++) {
      map.put(new Key(i), new Object());
    }
    map.clear();
    var largeDelegate = map.delegate();
    var first = new Key(1);
    var second = new Key(1);
    var firstValue = new Object();
    var secondValue = new Object();
    for (var cycle = 0; cycle < 10; cycle++) {
      map.put(first, firstValue);
      map.clear();
    }
    assertThat(map.delegate()).isNotSameAs(largeDelegate);

    assertThat(map.put(first, firstValue)).isNull();
    assertThat(map.put(second, secondValue)).isNull();
    assertThat(map.size()).isEqualTo(2);
    assertThat(map.containsKey(new Key(1))).isFalse();
    assertThat(map.get(first)).isSameAs(firstValue);
    assertThat(map.get(second)).isSameAs(secondValue);
    assertThat(map.put(first, secondValue)).isSameAs(firstValue);
    assertThat(map.size()).isEqualTo(2);
    assertThat(map.remove(new Key(1))).isNull();
    assertThat(map.remove(first)).isSameAs(secondValue);
    assertThat(map.containsKey(first)).isFalse();
    assertThat(map.get(second)).isSameAs(secondValue);
    assertThat(map.size()).isEqualTo(1);
  }

  private static void putAll(ShrinkingIdentityHashMap<Integer, Integer> map, int count) {
    for (var i = 0; i < count; i++) {
      Integer key = i;
      map.put(key, key);
    }
  }
}
