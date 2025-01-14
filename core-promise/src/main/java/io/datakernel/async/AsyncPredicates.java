package io.datakernel.async;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class AsyncPredicates {
	private AsyncPredicates() {}

	public static class AsyncPredicateWrapper<T> implements AsyncPredicate<T> {
		@NotNull
		private final Predicate<T> predicate;

		public AsyncPredicateWrapper(@NotNull Predicate<T> predicate) {this.predicate = predicate;}

		@Override
		public Promise<Boolean> test(T t) {
			return Promise.of(predicate.test(t));
		}

		@NotNull
		public Predicate<T> getPredicate() {
			return predicate;
		}
	}
}
