package io.datakernel.async;

import java.util.function.Predicate;

public interface AsyncPredicate<T> {
	Stage<Boolean> test(T t);

	default AsyncPredicate<T> negate() {
		return t -> this.test(t).thenApply(b -> !b);
	}

	default AsyncPredicate<T> and(AsyncPredicate<? super T> other) {
		return t -> test(t).combine(other.test(t), (b1, b2) -> b1 && b2);
	}

	default AsyncPredicate<T> or(AsyncPredicate<? super T> other) {
		return t -> test(t).combine(other.test(t), (b1, b2) -> b1 || b2);
	}

	static <T> AsyncPredicate<T> of(Predicate<T> predicate) {
		return t -> Stage.of(predicate.test(t));
	}

	static <T> AsyncPredicate<T> alwaysTrue() {
		return t -> Stage.of(Boolean.TRUE);
	}

	static <T> AsyncPredicate<T> alwaysFalse() {
		return t -> Stage.of(Boolean.FALSE);
	}

}