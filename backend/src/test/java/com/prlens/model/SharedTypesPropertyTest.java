package com.prlens.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.testkit.ConfigurationArbitraries;
import com.prlens.testkit.PullRequestSnapshotArbitraries;
import com.prlens.testkit.ReviewContextArbitraries;
import com.prlens.testkit.ReviewResultArbitraries;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * 공유 타입의 속성 테스트 (design.md Property 43). record 구성 요소를 리플렉션으로 훑어 하위 타입까지 본다.
 *
 * <p>하위 타입의 새 필드는 따라가지만, 새 루트 타입은 {@code sharedTypeInstances}에 직접 더해야 한다.
 */
class SharedTypesPropertyTest {

  /** 다시 만든 인스턴스와, 그 인스턴스를 만들 때 넘긴 수정 가능한 원본 컬렉션들. */
  private record Rebuilt(
      Object instance, List<List<Object>> sourceLists, List<Map<?, ?>> sourceMaps) {}

  @Provide
  Arbitrary<Record> sharedTypeInstances() {
    return Arbitraries.oneOf(
        PullRequestSnapshotArbitraries.snapshots(),
        ReviewContextArbitraries.contexts(),
        ReviewResultArbitraries.reviewResults(),
        ConfigurationArbitraries.configurations());
  }

  // Feature: pr-lens-p1-cli, Property 43: 공유 타입 불변성과 값 동등성
  @Property(tries = 200)
  void sharedTypesAreImmutableAndEqualByValue(@ForAll("sharedTypeInstances") Record instance) {
    List<List<Object>> exposedLists = new ArrayList<>();
    List<Map<?, ?>> exposedMaps = new ArrayList<>();
    collect(instance, exposedLists, exposedMaps);

    for (List<Object> list : exposedLists) {
      assertThatThrownBy(() -> list.add(new Object()))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(list::clear).isInstanceOf(UnsupportedOperationException.class);
    }
    for (Map<?, ?> map : exposedMaps) {
      assertThatThrownBy(map::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    Rebuilt rebuilt = rebuild(instance);
    assertThat(rebuilt.instance()).isNotSameAs(instance).isEqualTo(instance);
    assertThat(rebuilt.instance().hashCode()).isEqualTo(instance.hashCode());

    for (List<Object> source : rebuilt.sourceLists()) {
      source.clear();
      source.add(new Object());
    }
    rebuilt.sourceMaps().forEach(Map::clear);
    assertThat(rebuilt.instance()).isEqualTo(instance);
    assertThat(rebuilt.instance().hashCode()).isEqualTo(instance.hashCode());

    covers("PullRequestSnapshot", instance instanceof PullRequestSnapshot);
    covers("ReviewContext", instance instanceof ReviewContext);
    covers("ReviewResult", instance instanceof ReviewResult);
    covers("Configuration", instance instanceof Configuration);
    covers("비어 있지 않은 목록", exposedLists.stream().anyMatch(list -> !list.isEmpty()));
    covers("목록 안의 목록", exposedLists.stream().anyMatch(SharedTypesPropertyTest::holdsList));
    covers("비어 있지 않은 맵", exposedMaps.stream().anyMatch(map -> !map.isEmpty()));
  }

  /** 그 경우가 한 번 이상 생성됐는지 확인한다. */
  private static void covers(String label, boolean observed) {
    Statistics.label(label)
        .collect(observed)
        .coverage(coverage -> coverage.check(true).count(count -> count > 0));
  }

  /** 목록의 원소(하위 타입)가 다시 목록을 가지고 있는지 본다. */
  private static boolean holdsList(List<Object> list) {
    List<List<Object>> nested = new ArrayList<>();
    list.forEach(element -> collect(element, nested, new ArrayList<>()));
    return !nested.isEmpty();
  }

  /** {@code value}에서 닿을 수 있는 모든 목록과 맵을 모은다. */
  @SuppressWarnings("unchecked")
  private static void collect(Object value, List<List<Object>> lists, List<Map<?, ?>> maps) {
    if (value instanceof List<?> list) {
      lists.add((List<Object>) list);
      list.forEach(element -> collect(element, lists, maps));
    } else if (value instanceof SortedMap<?, ?> map) {
      maps.add(map);
      map.values().forEach(element -> collect(element, lists, maps));
    } else if (value instanceof Record record) {
      for (RecordComponent component : record.getClass().getRecordComponents()) {
        collect(read(record, component), lists, maps);
      }
    } else {
      rejectUnknownContainer(value);
    }
  }

  /** 검사 없이 지나가지 않도록, 이 테스트가 다루지 않는 컬렉션({@code Set}, 일반 {@code Map} 등)을 만나면 실패시킨다. */
  private static void rejectUnknownContainer(Object value) {
    if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
      throw new IllegalStateException(
          "이 테스트가 다루지 않는 컬렉션입니다. collect와 rebuild에 처리를 더하세요: " + value.getClass());
    }
  }

  private static Rebuilt rebuild(Record instance) {
    List<List<Object>> sourceLists = new ArrayList<>();
    List<Map<?, ?>> sourceMaps = new ArrayList<>();
    return new Rebuilt(rebuild(instance, sourceLists, sourceMaps), sourceLists, sourceMaps);
  }

  /** 같은 필드 값으로 record를 정식 생성자로 다시 만든다. 목록과 맵은 수정 가능한 새 컬렉션에 옮겨 넘긴다. */
  private static Object rebuild(
      Object value, List<List<Object>> sourceLists, List<Map<?, ?>> sourceMaps) {
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>();
      list.forEach(element -> copy.add(rebuild(element, sourceLists, sourceMaps)));
      sourceLists.add(copy);
      return copy;
    }
    if (value instanceof SortedMap<?, ?> map) {
      SortedMap<Object, Object> copy = new TreeMap<>();
      map.forEach((key, element) -> copy.put(key, rebuild(element, sourceLists, sourceMaps)));
      sourceMaps.add(copy);
      return copy;
    }
    if (value instanceof Record record) {
      RecordComponent[] components = record.getClass().getRecordComponents();
      Object[] arguments =
          Arrays.stream(components)
              .map(component -> rebuild(read(record, component), sourceLists, sourceMaps))
              .toArray();
      Class<?>[] types =
          Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
      try {
        return record.getClass().getDeclaredConstructor(types).newInstance(arguments);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(record.getClass() + "를 다시 만들 수 없습니다", e);
      }
    }
    rejectUnknownContainer(value);
    return value;
  }

  private static Object read(Record record, RecordComponent component) {
    try {
      return component.getAccessor().invoke(record);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(component + "를 읽을 수 없습니다", e);
    }
  }
}
