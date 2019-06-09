package io.datakernel.di.core;

import io.datakernel.di.error.CannotGenerateBindingException;
import io.datakernel.di.module.BindingGenerator;
import io.datakernel.di.module.BindingProvider;
import io.datakernel.di.module.BindingTransformer;
import io.datakernel.di.util.Trie;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static io.datakernel.di.util.ScopedValue.UNSCOPED;
import static io.datakernel.di.util.Types.findBestMatch;
import static io.datakernel.di.util.Utils.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public final class BindingGraph {
	public static final Binding<?> PHANTOM = Binding.to($ -> {
		throw new AssertionError("This binding exists as a marker to be replaced by generated binding, if you see this message then somethning is really wrong");
	});

	private BindingGraph() {
		throw new AssertionError("nope.");
	}

	public static void completeBindingGraph(Trie<Scope, Map<Key<?>, Binding<?>>> bindings,
			Map<Integer, BindingTransformer<?>> transformers,
			Map<Type, Set<BindingGenerator<?>>> generators) {
		completeBindingGraph(new HashMap<>(bindings.get()), UNSCOPED, bindings, transformers, generators);
	}

	private static void completeBindingGraph(Map<Key<?>, Binding<?>> known,
			Scope[] scope, Trie<Scope, Map<Key<?>, Binding<?>>> bindings,
			Map<Integer, BindingTransformer<?>> transformers,
			Map<Type, Set<BindingGenerator<?>>> generators) {
		bindings.getChildren().forEach((subscope, subtrie) -> completeBindingGraph(override(known, subtrie.get()), next(scope, subscope), subtrie, transformers, generators));
		completeBindingGraph(known, scope, bindings.get(), transformers, generators);
	}

	@SuppressWarnings("unchecked")
	private static void completeBindingGraph(Map<Key<?>, Binding<?>> known,
			Scope[] scope, Map<Key<?>, Binding<?>> localBindings,
			Map<Integer, BindingTransformer<?>> transformers,
			Map<Type, Set<BindingGenerator<?>>> generators) {
		Map<Key<?>, Set<BindingGenerator<?>>> generatorCache = new HashMap<>();

		List<BindingTransformer<?>> transformerList = transformers.entrySet().stream()
				.sorted(Comparator.comparing(Entry::getKey))
				.map(Entry::getValue)
				.collect(toList());

		Map<Key<?>, Binding<?>> generated = new HashMap<>();

		BindingProvider provider = new BindingProvider() {
			@Override
			@Nullable
			public <T> Binding<T> getBinding(Key<T> key) {
				Binding<T> binding = (Binding<T>) generated.get(key);
				if (binding == null) {
					binding = (Binding<T>) known.get(key);
				}
				if (binding != null && binding != PHANTOM) {
					return binding;
				}
				Set<BindingGenerator<?>> found = generatorCache.computeIfAbsent(key, k -> generators.get(findBestMatch(k.getType(), generators.keySet())));
				if (found == null) {
					return null;
				}
				Set<Binding<T>> generatedBindings = found.stream()
						.map(generator -> ((BindingGenerator<T>) generator).generate(scope, key, this))
						.filter(Objects::nonNull)
						.collect(toSet());

				if (generatedBindings.isEmpty()) {
					return null;
				}
				if (generatedBindings.size() > 1) {
					throw new CannotGenerateBindingException(key, "More than one generator provided a binding");
				}
				Binding<T> generatedBinding = generatedBindings.iterator().next();
				for (BindingTransformer<?> transformer : transformerList) {
					generatedBinding = ((BindingTransformer<T>) transformer).transform(scope, key, generatedBinding, this);
					if (generatedBinding == null) {
						throw new NullPointerException("Transformers should never return null");
					}
				}
				generated.put(key, generatedBinding);

				// ensure that its dependencies are generated if nesessary
				for (Dependency dependency : generatedBinding.getDependencies()) {
					getBinding(dependency.getKey());
				}
				return generatedBinding;
			}
		};

		for (Entry<Key<?>, Binding<?>> entry : localBindings.entrySet()) {
			Key<Object> key = (Key<Object>) entry.getKey();
			Binding<?> binding = entry.getValue();

			if (binding == PHANTOM) {
				Binding<Object> generatedBinding = provider.getBinding(key);
				if (generatedBinding == null) {
					// phantom bindings are the ones requested with plain `bind(...);` call, here we fail fast
					// see comment below where dependencies are generated
					throw new CannotGenerateBindingException(key, "Refused to generate a requested binding");
				}
				known.put(key, generatedBinding);
			}

			for (Dependency dependency : binding.getDependencies()) {
				Key<?> depKey = dependency.getKey();
				if (known.containsKey(depKey)) {
					continue;
				}
				known.put(depKey, provider.getBinding(depKey)); // put even nulls in known just as a little optimization
				// when generating dependencies we dont fail and just do nothing
				// unsatisfied dependency check will collect all of them and make a nice error
			}
		}
		localBindings.putAll(generated);
	}

	/**
	 * This method returns mapping from *unstatisfied keys* to *bindings that require them*
	 * and not the common *key and the bindings that provide it*
	 */
	public static Map<Key<?>, Set<Binding<?>>> getUnsatisfiedDependencies(Trie<Scope, Map<Key<?>, Binding<?>>> bindings) {
		return getUnsatisfiedDependencies(new HashSet<>(bindings.get().keySet()), bindings)
				.collect(toMultimap(dtb -> dtb.key, dtb -> dtb.binding));
	}

	private static Stream<DependencyToBinding> getUnsatisfiedDependencies(Set<Key<?>> known, Trie<Scope, Map<Key<?>, Binding<?>>> bindings) {
		return Stream.concat(
				bindings.get().values().stream()
						.flatMap(binding -> Arrays.stream(binding.getDependencies())
								.filter(dependency -> dependency.isRequired() && !known.contains(dependency.getKey()))
								.map(dependency -> new DependencyToBinding(dependency.getKey(), binding))),
				bindings.getChildren().values().stream().flatMap(scopeBindings -> getUnsatisfiedDependencies(union(known, scopeBindings.get().keySet()), scopeBindings))
		);
	}

	private static class DependencyToBinding {
		Key<?> key;
		Binding<?> binding;

		public DependencyToBinding(Key<?> key, Binding<?> binding) {
			this.key = key;
			this.binding = binding;
		}
	}

	public static Set<Key<?>[]> getCyclicDependencies(Trie<Scope, Map<Key<?>, Binding<?>>> bindings) {
		return getCyclicDependencies(new HashSet<>(), bindings).collect(toSet());
	}

	private static Stream<Key<?>[]> getCyclicDependencies(Set<Key<?>> visited, Trie<Scope, Map<Key<?>, Binding<?>>> bindings) {
		return Stream.concat(
				dfs(visited, bindings.get()).stream(),
				bindings.getChildren().values().stream().flatMap(scopeBindings -> getCyclicDependencies(new HashSet<>(visited), scopeBindings))
		);
	}

	private static Set<Key<?>[]> dfs(Set<Key<?>> visited, Map<Key<?>, Binding<?>> bindings) {
		LinkedHashSet<Key<?>> visiting = new LinkedHashSet<>();
		Set<Key<?>[]> cycles = new HashSet<>();
		for (Key<?> key : bindings.keySet()) {
			dfs(bindings, visited, visiting, cycles, key);
		}
		return cycles;
	}

	private static void dfs(Map<Key<?>, Binding<?>> bindings, Set<Key<?>> visited, LinkedHashSet<Key<?>> visiting, Set<Key<?>[]> cycles, Key<?> key) {
		if (visited.contains(key)) {
			return;
		}
		Binding<?> binding = bindings.get(key);
		if (binding == null) {
			// just ignore unsatisfied dependencies as if they never existed
			visited.add(key); // add to visited as a tiny optimization
			return;
		}
		if (!visiting.add(key)) {
			// so at this point visiting set looks something like a -> b -> c -> d -> e -> g -> c,
			// and in the code below we just get d -> e -> g -> c out of it
			Iterator<Key<?>> backtracked = visiting.iterator();
			int skipped = 0;
			while (backtracked.hasNext() && !backtracked.next().equals(key)) {
				skipped++;
			}
			Key<?>[] cycle = new Key[visiting.size() - skipped];
			for (int i = 0; i < cycle.length - 1; i++) {
				cycle[i] = backtracked.next(); // call to next() without hasNext() should be ok here
			}
			cycle[cycle.length - 1] = key;
			cycles.add(cycle);
			return;
		}
		for (Dependency dependency : binding.getDependencies()) {
			dfs(bindings, visited, visiting, cycles, dependency.getKey());
		}
		visiting.remove(key);
		visited.add(key);
	}
}
