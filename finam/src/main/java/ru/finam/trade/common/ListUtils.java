package ru.finam.trade.common;

import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

@UtilityClass
public class ListUtils {
  public static <T> List<T> concat(List<T> l1, T el) {
    return Optional.ofNullable(el)
      .map(e -> concat(l1, List.of(e)))
      .orElse(l1);
  }

  public static <T> List<T> concat(List<T> l1, List<T> l2) {
    val res = new ArrayList<>(l1);
    Optional.ofNullable(l2).ifPresent(res::addAll);
    return res;
  }

  public static <T> Optional<T> getLast(List<T> l1) {
    return !l1.isEmpty() ? Optional.of(l1.get(l1.size() - 1)) : Optional.empty();
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }
}
