/*
 * Copyright (C) 2015-2018 SoftIndex LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.datakernel.aggregation;

import io.datakernel.aggregation.fieldtype.FieldType;
import io.datakernel.annotation.Nullable;
import io.datakernel.codegen.Expression;
import io.datakernel.codegen.Expressions;
import io.datakernel.codegen.PredicateDef;
import io.datakernel.codegen.Property;

import java.util.*;
import java.util.regex.Pattern;

import static io.datakernel.codegen.Expressions.*;
import static io.datakernel.util.CollectionUtils.first;
import static io.datakernel.util.Preconditions.checkState;
import static java.util.Arrays.asList;
import static java.util.Collections.*;

@SuppressWarnings("rawtypes")
public class AggregationPredicates {
	private AggregationPredicates() {
	}

	private static class PredicateSimplifierKey<L extends AggregationPredicate, R extends AggregationPredicate> {
		private final Class<L> leftType;
		private final Class<R> rightType;

		private PredicateSimplifierKey(Class<L> leftType, Class<R> rightType) {
			this.leftType = leftType;
			this.rightType = rightType;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateSimplifierKey that = (PredicateSimplifierKey) o;

			if (!leftType.equals(that.leftType)) return false;
			return rightType.equals(that.rightType);

		}

		@Override
		public int hashCode() {
			int result = leftType.hashCode();
			result = 31 * result + rightType.hashCode();
			return result;
		}
	}

	@FunctionalInterface
	private interface PredicateSimplifier<L extends AggregationPredicate, R extends AggregationPredicate> {
		AggregationPredicate simplifyAnd(L left, R right);
	}

	private final static Map<PredicateSimplifierKey<?, ?>, PredicateSimplifier<?, ?>> simplifiers = new HashMap<>();

	private static <L extends AggregationPredicate, R extends AggregationPredicate> void register(Class<L> leftType, Class<R> rightType, PredicateSimplifier<L, R> operation) {
		PredicateSimplifierKey keyLeftRight = new PredicateSimplifierKey<>(leftType, rightType);
		checkState(!simplifiers.containsKey(keyLeftRight));
		simplifiers.put(keyLeftRight, operation);
		if (!rightType.equals(leftType)) {
			PredicateSimplifierKey keyRightLeft = new PredicateSimplifierKey<>(rightType, leftType);
			checkState(!simplifiers.containsKey(keyRightLeft));
			simplifiers.put(keyRightLeft, (PredicateSimplifier<R, L>) (right, left) -> operation.simplifyAnd(left, right));
		}
	}

	static {
		PredicateSimplifier simplifierAlwaysFalse = new PredicateSimplifier<PredicateAlwaysFalse, AggregationPredicate>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateAlwaysFalse left, AggregationPredicate right) {
				return left;
			}
		};
		register(PredicateAlwaysFalse.class, PredicateAlwaysFalse.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateAlwaysTrue.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateNot.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateEq.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateNotEq.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateLe.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateGe.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateLt.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateGt.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateHas.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateBetween.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateRegexp.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateAnd.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateOr.class, simplifierAlwaysFalse);
		register(PredicateAlwaysFalse.class, PredicateIn.class, simplifierAlwaysFalse);

		PredicateSimplifier simplifierAlwaysTrue = new PredicateSimplifier<PredicateAlwaysTrue, AggregationPredicate>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateAlwaysTrue left, AggregationPredicate right) {
				return right;
			}
		};
		register(PredicateAlwaysTrue.class, PredicateAlwaysTrue.class, simplifierAlwaysTrue);
		register(PredicateAlwaysTrue.class, PredicateNot.class, simplifierAlwaysTrue);
		register(PredicateAlwaysTrue.class, PredicateEq.class, simplifierAlwaysTrue);
		register(PredicateAlwaysTrue.class, PredicateNotEq.class, simplifierAlwaysTrue);
		register(PredicateAlwaysTrue.class, PredicateLe.class, simplifierAlwaysTrue);
		register(PredicateAlwaysTrue.class, PredicateGe.class, simplifierAlwaysTrue);
		register(PredicateAlwaysTrue.class, PredicateLt.class, simplifierAlwaysTrue);
		register(PredicateAlwaysTrue.class, PredicateGt.class, simplifierAlwaysTrue);
		register(PredicateAlwaysTrue.class, PredicateHas.class, simplifierAlwaysTrue);
		register(PredicateAlwaysTrue.class, PredicateBetween.class, simplifierAlwaysTrue);
		register(PredicateAlwaysTrue.class, PredicateRegexp.class, simplifierAlwaysTrue);
		register(PredicateAlwaysTrue.class, PredicateAnd.class, simplifierAlwaysTrue);
		register(PredicateAlwaysTrue.class, PredicateOr.class, simplifierAlwaysTrue);
		register(PredicateAlwaysTrue.class, PredicateIn.class, simplifierAlwaysTrue);

		PredicateSimplifier simplifierNot = new PredicateSimplifier<PredicateNot, AggregationPredicate>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateNot left, AggregationPredicate right) {
				if (left.predicate.equals(right))
					return alwaysFalse();
				return null;
			}
		};
		register(PredicateNot.class, PredicateNot.class, simplifierNot);
		register(PredicateNot.class, PredicateHas.class, simplifierNot);
		register(PredicateNot.class, PredicateBetween.class, simplifierNot);
		register(PredicateNot.class, PredicateRegexp.class, simplifierNot);
		register(PredicateNot.class, PredicateAnd.class, simplifierNot);
		register(PredicateNot.class, PredicateOr.class, simplifierNot);
		register(PredicateNot.class, PredicateGe.class, simplifierNot);
		register(PredicateNot.class, PredicateLe.class, simplifierNot);
		register(PredicateNot.class, PredicateGt.class, simplifierNot);
		register(PredicateNot.class, PredicateLt.class, simplifierNot);
		register(PredicateNot.class, PredicateIn.class, simplifierNot);

		register(PredicateHas.class, PredicateHas.class, new PredicateSimplifier<PredicateHas, PredicateHas>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateHas left, PredicateHas right) {
				return left.key.equals(right.key) ? left : null;
			}
		});
		PredicateSimplifier simplifierHas = new PredicateSimplifier<PredicateHas, AggregationPredicate>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateHas left, AggregationPredicate right) {
				return right.getDimensions().contains(left.getKey()) ? right : null;
			}
		};
		register(PredicateHas.class, PredicateEq.class, simplifierHas);
		register(PredicateHas.class, PredicateNotEq.class, (left, right) -> left.key.equals(right.key) ? left : null);
		register(PredicateHas.class, PredicateLe.class, simplifierHas);
		register(PredicateHas.class, PredicateGe.class, simplifierHas);
		register(PredicateHas.class, PredicateLt.class, simplifierHas);
		register(PredicateHas.class, PredicateGt.class, simplifierHas);
		register(PredicateHas.class, PredicateBetween.class, simplifierHas);
		register(PredicateHas.class, PredicateAnd.class, simplifierHas);
		register(PredicateHas.class, PredicateOr.class, simplifierHas);
		register(PredicateHas.class, PredicateIn.class, simplifierHas);

		register(PredicateEq.class, PredicateEq.class, new PredicateSimplifier<PredicateEq, PredicateEq>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateEq left, PredicateEq right) {
				if (!left.key.equals(right.key))
					return null;
				return alwaysFalse();
			}
		});
		register(PredicateEq.class, PredicateNotEq.class, new PredicateSimplifier<PredicateEq, PredicateNotEq>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateEq left, PredicateNotEq right) {
				if (!left.key.equals(right.key))
					return null;
				if (!left.value.equals(right.value))
					return left;
				return alwaysFalse();
			}
		});
		register(PredicateEq.class, PredicateLe.class, new PredicateSimplifier<PredicateEq, PredicateLe>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateEq left, PredicateLe right) {
				if (!left.key.equals(right.key))
					return null;
				if (right.value.compareTo(left.value) >= 0)
					return left;
				return alwaysFalse();
			}
		});
		register(PredicateEq.class, PredicateGe.class, new PredicateSimplifier<PredicateEq, PredicateGe>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateEq left, PredicateGe right) {
				if (!left.key.equals(right.key))
					return null;
				if (right.value.compareTo(left.value) <= 0)
					return left;
				return alwaysFalse();
			}
		});
		register(PredicateEq.class, PredicateLt.class, new PredicateSimplifier<PredicateEq, PredicateLt>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateEq left, PredicateLt right) {
				if (!left.key.equals(right.key))
					return null;
				if (right.value.compareTo(left.value) > 0)
					return left;
				return alwaysFalse();
			}
		});
		register(PredicateEq.class, PredicateGt.class, new PredicateSimplifier<PredicateEq, PredicateGt>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateEq left, PredicateGt right) {
				if (!left.key.equals(right.key))
					return null;
				if (right.value.compareTo(left.value) < 0)
					return left;
				return alwaysFalse();
			}
		});
		register(PredicateEq.class, PredicateBetween.class, new PredicateSimplifier<PredicateEq, PredicateBetween>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateEq left, PredicateBetween right) {
				if (!left.key.equals(right.key))
					return null;
				if (right.from.compareTo(left.value) <= 0 && right.to.compareTo(left.value) >= 0)
					return left;
				return alwaysFalse();
			}
		});
		register(PredicateEq.class, PredicateRegexp.class, new PredicateSimplifier<PredicateEq, PredicateRegexp>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateEq left, PredicateRegexp right) {
				if (!left.key.equals(right.key))
					return null;
				Pattern p = Pattern.compile(right.regexp);
				if (p.matcher(left.key).matches())
					return left;
				return alwaysFalse();
			}
		});
		register(PredicateEq.class, PredicateIn.class, new PredicateSimplifier<PredicateEq, PredicateIn>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateEq left, PredicateIn right) {
				if (!left.key.equals(right.key))
					return null;
				if (right.values.contains(left.value))
					return left;
				return alwaysFalse();
			}
		});

		register(PredicateNotEq.class, PredicateNotEq.class, new PredicateSimplifier<PredicateNotEq, PredicateNotEq>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateNotEq left, PredicateNotEq right) {
				if (!left.key.equals(right.key))
					return null;
				if (left.value.equals(right.value))
					return left;
				return null;
			}
		});
		register(PredicateNotEq.class, PredicateLe.class, new PredicateSimplifier<PredicateNotEq, PredicateLe>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateNotEq left, PredicateLe right) {
				if (!left.key.equals(right.key))
					return null;
				if (right.value.compareTo(left.value) < 0)
					return right;
				if (right.value.compareTo(left.value) == 0)
					return lt(left.key, right.value);
				return null;
			}
		});
		register(PredicateNotEq.class, PredicateGe.class, new PredicateSimplifier<PredicateNotEq, PredicateGe>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateNotEq left, PredicateGe right) {
				if (!left.key.equals(right.key))
					return null;
				if (right.value.compareTo(left.value) > 0)
					return right;
				if (right.value.compareTo(left.value) == 0)
					return gt(left.key, right.value);
				return null;
			}
		});
		register(PredicateNotEq.class, PredicateLt.class, new PredicateSimplifier<PredicateNotEq, PredicateLt>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateNotEq left, PredicateLt right) {
				if (!left.key.equals(right.key))
					return null;
				if (right.value.compareTo(left.value) <= 0)
					return right;
				return null;
			}
		});
		register(PredicateNotEq.class, PredicateGt.class, new PredicateSimplifier<PredicateNotEq, PredicateGt>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateNotEq left, PredicateGt right) {
				if (!left.key.equals(right.key))
					return null;
				if (right.value.compareTo(left.value) >= 0)
					return right;
				return null;
			}
		});
		register(PredicateNotEq.class, PredicateBetween.class, new PredicateSimplifier<PredicateNotEq, PredicateBetween>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateNotEq left, PredicateBetween right) {
				if (!right.key.equals(left.key))
					return null;
				if (right.from.compareTo(left.value) > 0 && right.to.compareTo(left.value) > 0)
					return right;
				if (right.from.compareTo(left.value) < 0 && right.to.compareTo(left.value) < 0)
					return right;
				return null;
			}
		});

		register(PredicateLe.class, PredicateLe.class, new PredicateSimplifier<PredicateLe, PredicateLe>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateLe left, PredicateLe right) {
				if (!right.key.equals(left.key))
					return null;
				if (right.value.compareTo(left.value) <= 0)
					return right;
				return left;
			}
		});
		register(PredicateLe.class, PredicateGe.class, new PredicateSimplifier<PredicateLe, PredicateGe>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateLe left, PredicateGe right) {
				if (!left.key.equals(right.key))
					return null;
				if (left.value.compareTo(right.value) < 0)
					return alwaysFalse();
				if (left.value.compareTo(right.value) > 0)
					return between(right.key, right.value, left.value);
				if (left.value.compareTo(right.value) == 0)
					return eq(left.key, left.value);
				return null;
			}
		});
		register(PredicateLe.class, PredicateLt.class, new PredicateSimplifier<PredicateLe, PredicateLt>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateLe left, PredicateLt right) {
				if (!right.key.equals(left.key))
					return null;
				if (right.value.compareTo(left.value) <= 0)
					return right;
				return left;
			}
		});
		register(PredicateLe.class, PredicateGt.class, new PredicateSimplifier<PredicateLe, PredicateGt>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateLe left, PredicateGt right) {
				if (!left.key.equals(right.key))
					return null;
				if (left.value.compareTo(right.value) <= 0)
					return alwaysFalse();
				return null;
			}
		});
		register(PredicateLe.class, PredicateBetween.class, new PredicateSimplifier<PredicateLe, PredicateBetween>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateLe left, PredicateBetween right) {
				if (!right.key.equals(left.key))
					return null;
				if (right.from.compareTo(left.value) > 0)
					return alwaysFalse();
				if (right.from.compareTo(left.value) == 0)
					return eq(left.key, right.from);
				if (right.to.compareTo(left.value) <= 0)
					return right;
				return between(right.key, right.from, left.value).simplify();
			}
		});
		register(PredicateLe.class, PredicateIn.class, new PredicateSimplifier<PredicateLe, PredicateIn>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateLe left, PredicateIn right) {
				if (!left.key.equals(right.key))
					return null;
				if (left.value.compareTo(right.values.last()) >= 0)
					return right;
				if (left.value.compareTo(right.values.first()) < 0)
					return alwaysFalse();
				SortedSet subset = new TreeSet(right.values.headSet(left.value));
				if (right.values.contains(left.value)) subset.add(left.value);
				return in(left.key, subset);
			}
		});

		register(PredicateGe.class, PredicateGe.class, new PredicateSimplifier<PredicateGe, PredicateGe>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateGe left, PredicateGe right) {
				if (!right.key.equals(left.key))
					return null;
				if (right.value.compareTo(left.value) >= 0)
					return right;
				return left;
			}
		});
		register(PredicateGe.class, PredicateLt.class, new PredicateSimplifier<PredicateGe, PredicateLt>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateGe left, PredicateLt right) {
				if (!right.key.equals(left.key))
					return null;
				if (right.value.compareTo(left.value) <= 0)
					return alwaysFalse();
				return null;
			}
		});
		register(PredicateGe.class, PredicateGt.class, new PredicateSimplifier<PredicateGe, PredicateGt>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateGe left, PredicateGt right) {
				if (!right.key.equals(left.key))
					return null;
				if (right.value.compareTo(left.value) >= 0)
					return gt(right.key, right.value);
				return left;
			}
		});
		register(PredicateGe.class, PredicateBetween.class, new PredicateSimplifier<PredicateGe, PredicateBetween>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateGe left, PredicateBetween right) {
				if (!right.key.equals(left.key))
					return null;
				if (right.to.compareTo(left.value) < 0)
					return alwaysFalse();
				if (right.to.compareTo(left.value) == 0)
					return eq(right.key, right.to);
				if (right.from.compareTo(left.value) >= 0)
					return right;
				return between(right.key, left.value, right.to).simplify();
			}
		});
		register(PredicateGe.class, PredicateIn.class, new PredicateSimplifier<PredicateGe, PredicateIn>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateGe left, PredicateIn right) {
				if (!left.key.equals(right.key))
					return null;
				if (left.value.compareTo(right.values.first()) <= 0)
					return right;
				if (left.value.compareTo(right.values.last()) > 0)
					return alwaysFalse();
				return in(left.key, new TreeSet(right.values.tailSet(left.value)));
			}
		});

		register(PredicateLt.class, PredicateLt.class, new PredicateSimplifier<PredicateLt, PredicateLt>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateLt left, PredicateLt right) {
				if (!right.key.equals(left.key))
					return null;
				if (right.value.compareTo(left.value) >= 0)
					return left;
				return right;
			}
		});
		register(PredicateLt.class, PredicateGt.class, new PredicateSimplifier<PredicateLt, PredicateGt>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateLt left, PredicateGt right) {
				if (!left.key.equals(right.key))
					return null;
				if (left.value.compareTo(right.value) <= 0)
					return alwaysFalse();
				return null;
			}
		});
		register(PredicateLt.class, PredicateBetween.class, new PredicateSimplifier<PredicateLt, PredicateBetween>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateLt left, PredicateBetween right) {
				if (!right.key.equals(left.key))
					return null;
				if (right.from.compareTo(left.value) >= 0)
					return alwaysFalse();
				if (right.to.compareTo(left.value) < 0)
					return right;
				return null;
			}
		});
		register(PredicateLt.class, PredicateIn.class, new PredicateSimplifier<PredicateLt, PredicateIn>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateLt left, PredicateIn right) {
				if (!left.key.equals(right.key))
					return null;
				if (left.value.compareTo(right.values.last()) > 0)
					return right;
				if (left.value.compareTo(right.values.first()) < 0)
					return alwaysFalse();
				return in(left.key, new TreeSet(right.values.subSet(right.values.first(), left.value)));
			}
		});

		register(PredicateGt.class, PredicateGt.class, new PredicateSimplifier<PredicateGt, PredicateGt>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateGt left, PredicateGt right) {
				if (!right.key.equals(left.key))
					return null;
				if (right.value.compareTo(left.value) >= 0)
					return right;
				return left;
			}
		});
		register(PredicateGt.class, PredicateBetween.class, new PredicateSimplifier<PredicateGt, PredicateBetween>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateGt left, PredicateBetween right) {
				if (!right.key.equals(left.key))
					return null;
				if (right.to.compareTo(left.value) <= 0)
					return alwaysFalse();
				if (right.from.compareTo(left.value) > 0)
					return right;
				return null;
			}
		});
		register(PredicateGt.class, PredicateIn.class, new PredicateSimplifier<PredicateGt, PredicateIn>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateGt left, PredicateIn right) {
				if (!left.key.equals(right.key))
					return null;
				if (left.value.compareTo(right.values.first()) < 0)
					return right;
				if (left.value.compareTo(right.values.last()) >= 0)
					return alwaysFalse();
				SortedSet subset = right.values.tailSet(left.value);
				subset.remove(left.value);
				return in(right.key, subset);
			}
		});

		register(PredicateBetween.class, PredicateBetween.class, new PredicateSimplifier<PredicateBetween, PredicateBetween>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateBetween left, PredicateBetween right) {
				if (!left.key.equals(right.key))
					return null;
				Comparable from = left.from.compareTo(right.from) >= 0 ? left.from : right.from;
				Comparable to = left.to.compareTo(right.to) <= 0 ? left.to : right.to;
				return between(left.key, from, to).simplify();
			}
		});
		register(PredicateBetween.class, PredicateIn.class, new PredicateSimplifier<PredicateBetween, PredicateIn>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateBetween left, PredicateIn right) {
				if (!left.key.equals(right.key))
					return null;
				if (left.from.compareTo(right.values.first()) > 0 && left.to.compareTo(right.values.last()) > 0)
					return left;
				return null;
			}
		});

		register(PredicateIn.class, PredicateIn.class, new PredicateSimplifier<PredicateIn, PredicateIn>() {
			@Override
			public AggregationPredicate simplifyAnd(PredicateIn left, PredicateIn right) {
				if (!left.key.equals(right.key))
					return null;
				if (left.values.equals(right.values))
					return left.values.size() == 1 ? eq(left.getKey(), left.values.first()) : left;
				SortedSet values = left.values;
				values.retainAll(right.values);
				if (values.size() == 1)
					return eq(left.key, left.values.first());
				if (!left.values.isEmpty())
					return in(left.key, left.values);
				return alwaysFalse();
			}
		});
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private static AggregationPredicate simplifyAnd(AggregationPredicate left, AggregationPredicate right) {
		if (left.equals(right))
			return left;
		PredicateSimplifierKey key = new PredicateSimplifierKey(left.getClass(), right.getClass());
		PredicateSimplifier<AggregationPredicate, AggregationPredicate> simplifier = (PredicateSimplifier<AggregationPredicate, AggregationPredicate>) simplifiers.get(key);
		if (simplifier == null)
			return null;
		return simplifier.simplifyAnd(left, right);
	}

	public static final class PredicateAlwaysFalse implements AggregationPredicate {
		private static final PredicateAlwaysFalse instance = new PredicateAlwaysFalse();

		private PredicateAlwaysFalse() {
		}

		@Override
		public AggregationPredicate simplify() {
			return this;
		}

		@Override
		public Set<String> getDimensions() {
			return emptySet();
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return emptyMap();
		}

		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			return Expressions.alwaysFalse();
		}

		@Override
		public String toString() {
			return "FALSE";
		}
	}

	public static final class PredicateAlwaysTrue implements AggregationPredicate {
		private static final PredicateAlwaysTrue instance = new PredicateAlwaysTrue();

		private PredicateAlwaysTrue() {
		}

		@Override
		public AggregationPredicate simplify() {
			return this;
		}

		@Override
		public Set<String> getDimensions() {
			return emptySet();
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return emptyMap();
		}

		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			return Expressions.alwaysTrue();
		}

		@Override
		public String toString() {
			return "TRUE";
		}
	}

	public static final class PredicateNot implements AggregationPredicate {
		private final AggregationPredicate predicate;

		private PredicateNot(AggregationPredicate predicate) {
			this.predicate = predicate;
		}

		public AggregationPredicate getPredicate() {
			return predicate;
		}

		@Override
		public AggregationPredicate simplify() {
			if (predicate instanceof PredicateNot)
				return ((PredicateNot) predicate).predicate.simplify();

			if (predicate instanceof PredicateEq)
				return new PredicateNotEq(((PredicateEq) this.predicate).key, ((PredicateEq) this.predicate).value);

			if (predicate instanceof PredicateNotEq)
				return new PredicateEq(((PredicateNotEq) this.predicate).key, ((PredicateNotEq) this.predicate).value);

			return not(predicate.simplify());
		}

		@Override
		public Set<String> getDimensions() {
			return predicate.getDimensions();
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return emptyMap();
		}

		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			return Expressions.not(predicate.createPredicateDef(record, fields));
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateNot that = (PredicateNot) o;

			return predicate.equals(that.predicate);

		}

		@Override
		public int hashCode() {
			return predicate.hashCode();
		}

		@Override
		public String toString() {
			return "NOT " + predicate;
		}
	}

	public static final class PredicateEq implements AggregationPredicate {
		final String key;
		final Object value;

		private PredicateEq(String key, Object value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public Object getValue() {
			return value;
		}

		@Override
		public AggregationPredicate simplify() {
			return this;
		}

		@Override
		public Set<String> getDimensions() {
			return singleton(key);
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return singletonMap(key, value);
		}

		@SuppressWarnings("unchecked")
		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			return (fields.get(key) == null)
					? Expressions.isNull(property(record, key.replace('.', '$')))
					: Expressions.and(isNotNull(property(record, key.replace('.', '$')), fields.get(key)),
					cmpEq(property(record, key.replace('.', '$')), value(toInternalValue(fields, key, value))));
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateEq that = (PredicateEq) o;

			if (!key.equals(that.key)) return false;
			return value != null ? value.equals(that.value) : that.value == null;
		}

		@Override
		public int hashCode() {
			int result = key.hashCode();
			result = 31 * result + (value != null ? value.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			return key + '=' + value;
		}
	}

	public static final class PredicateNotEq implements AggregationPredicate {
		final String key;
		final Object value;

		private PredicateNotEq(String key, Object value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public Object getValue() {
			return value;
		}

		@Override
		public AggregationPredicate simplify() {
			return this;
		}

		@Override
		public Set<String> getDimensions() {
			return singleton(key);
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return emptyMap();
		}

		@SuppressWarnings("unchecked")
		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			return (fields.get(key) == null)
					? Expressions.isNotNull(property(record, key.replace('.', '$')))
					: Expressions.or(isNull(property(record, key.replace('.', '$')), fields.get(key)),
					cmpNe(property(record, key.replace('.', '$')), value(toInternalValue(fields, key, value))));
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateNotEq that = (PredicateNotEq) o;

			if (!key.equals(that.key)) return false;
			return value != null ? value.equals(that.value) : that.value == null;
		}

		@Override
		public int hashCode() {
			int result = key.hashCode();
			result = 31 * result + (value != null ? value.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			return key + "!=" + value;
		}
	}

	public static final class PredicateLe implements AggregationPredicate {
		final String key;
		final Comparable value;

		private PredicateLe(String key, Comparable value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public Object getValue() {
			return value;
		}

		@Override
		public AggregationPredicate simplify() {
			return this;
		}

		@Override
		public Set<String> getDimensions() {
			return singleton(key);
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return emptyMap();
		}

		@SuppressWarnings("unchecked")
		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			Property property = property(record, key.replace('.', '$'));
			return Expressions.and(isNotNull(property, fields.get(key)),
					cmpLe(property, value(toInternalValue(fields, key, value))));
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateLe that = (PredicateLe) o;

			if (!key.equals(that.key)) return false;
			return value != null ? value.equals(that.value) : that.value == null;
		}

		@Override
		public int hashCode() {
			int result = key.hashCode();
			result = 31 * result + (value != null ? value.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			return key + "<=" + value;
		}
	}

	public static final class PredicateLt implements AggregationPredicate {
		final String key;
		final Comparable value;

		private PredicateLt(String key, Comparable value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public Object getValue() {
			return value;
		}

		@Override
		public AggregationPredicate simplify() {
			return this;
		}

		@Override
		public Set<String> getDimensions() {
			return singleton(key);
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return emptyMap();
		}

		@SuppressWarnings("unchecked")
		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			Property field = property(record, key.replace('.', '$'));
			return Expressions.and(isNotNull(field, fields.get(key)),
					cmpLt(field, value(toInternalValue(fields, key, value))));
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateLt that = (PredicateLt) o;

			if (!key.equals(that.key)) return false;
			return value != null ? value.equals(that.value) : that.value == null;
		}

		@Override
		public int hashCode() {
			int result = key.hashCode();
			result = 31 * result + (value != null ? value.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			return key + "<" + value;
		}
	}

	public static final class PredicateGe implements AggregationPredicate {
		final String key;
		final Comparable value;

		private PredicateGe(String key, Comparable value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public Comparable getValue() {
			return value;
		}

		@Override
		public AggregationPredicate simplify() {
			return this;
		}

		@Override
		public Set<String> getDimensions() {
			return singleton(key);
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return emptyMap();
		}

		@SuppressWarnings("unchecked")
		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			Property property = property(record, key.replace('.', '$'));
			return Expressions.and(isNotNull(property, fields.get(key)),
					cmpGe(property, value(toInternalValue(fields, key, value))));
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateGe that = (PredicateGe) o;

			if (!key.equals(that.key)) return false;
			return value != null ? value.equals(that.value) : that.value == null;
		}

		@Override
		public int hashCode() {
			int result = key.hashCode();
			result = 31 * result + (value != null ? value.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			return key + ">=" + value;
		}
	}

	public static final class PredicateGt implements AggregationPredicate {
		final String key;
		final Comparable value;

		private PredicateGt(String key, Comparable value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public Comparable getValue() {
			return value;
		}

		@Override
		public AggregationPredicate simplify() {
			return this;
		}

		@Override
		public Set<String> getDimensions() {
			return singleton(key);
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return emptyMap();
		}

		@SuppressWarnings("unchecked")
		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			Property property = property(record, key.replace('.', '$'));
			return Expressions.and(isNotNull(property, fields.get(key)),
					cmpGt(property, value(toInternalValue(fields, key, value))));
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateGt that = (PredicateGt) o;

			if (!key.equals(that.key)) return false;
			return value != null ? value.equals(that.value) : that.value == null;
		}

		@Override
		public int hashCode() {
			int result = key.hashCode();
			result = 31 * result + (value != null ? value.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			return key + ">" + value;
		}
	}

	public static final class PredicateHas implements AggregationPredicate {
		final String key;

		private PredicateHas(String key) {
			this.key = key;
		}

		public String getKey() {
			return key;
		}

		@Override
		public AggregationPredicate simplify() {
			return this;
		}

		@Override
		public Set<String> getDimensions() {
			return emptySet();
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return emptyMap();
		}

		@SuppressWarnings("unchecked")
		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			return fields.containsKey(key) ? Expressions.alwaysTrue() : Expressions.alwaysFalse();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateHas that = (PredicateHas) o;

			return key.equals(that.key);

		}

		@Override
		public int hashCode() {
			int result = key.hashCode();
			result = 31 * result;
			return result;
		}

		@Override
		public String toString() {
			return "HAS " + key;
		}
	}

	public static final class PredicateRegexp implements AggregationPredicate {
		final String key;
		final String regexp;

		private PredicateRegexp(String key, String regexp) {
			this.key = key;
			this.regexp = regexp;
		}

		public String getKey() {
			return key;
		}

		public String getRegexp() {
			return regexp;
		}

		@Override
		public AggregationPredicate simplify() {
			return this;
		}

		@Override
		public Set<String> getDimensions() {
			return singleton(key);
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return emptyMap();
		}

		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			Pattern pattern = Pattern.compile(regexp);
			return Expressions.and(cmpNe(value(false), call(call(value(pattern), "matcher",
					cast(callStatic(String.class, "valueOf", cast(property(record, key.replace('.', '$')), Object.class)),
							CharSequence.class)), "matches")));
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateRegexp that = (PredicateRegexp) o;

			if (!key.equals(that.key)) return false;
			return regexp.equals(that.regexp);

		}

		@Override
		public int hashCode() {
			int result = key.hashCode();
			result = 31 * result + regexp.hashCode();
			return result;
		}

		@Override
		public String toString() {
			return key + " " + regexp;
		}
	}

	public static final class PredicateIn implements AggregationPredicate {
		final String key;
		final SortedSet values;

		PredicateIn(String key, SortedSet values) {
			this.key = key;
			this.values = values;
		}

		public String getKey() {
			return key;
		}

		public Set getValues() {
			return values;
		}

		@Override
		public AggregationPredicate simplify() {
			return (values.iterator().hasNext()) ? this : alwaysFalse();
		}

		@Override
		public Set<String> getDimensions() {
			return singleton(key);
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return emptyMap();
		}

		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			return Expressions.and(cmpNe(value(false), call(value(values), "contains",
					cast(property(record, key.replace('.', '$')), Object.class))));
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateIn that = (PredicateIn) o;

			if (!key.equals(that.key)) return false;
			return values != null ? values.equals(that.values) : that.values == null;

		}

		@Override
		public int hashCode() {
			int result = key.hashCode();
			result = 31 * result + (values != null ? values.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner(", ");
			for (Object value : values) joiner.add(value != null ? value.toString() : null);
			return "" + key + " IN " + joiner.toString();
		}
	}

	public static final class PredicateBetween implements AggregationPredicate {
		final String key;
		final Comparable from;
		final Comparable to;

		PredicateBetween(String key, Comparable from, Comparable to) {
			this.key = key;
			this.from = from;
			this.to = to;
		}

		public String getKey() {
			return key;
		}

		public Comparable getFrom() {
			return from;
		}

		public Comparable getTo() {
			return to;
		}

		@Override
		public AggregationPredicate simplify() {
			return (from.compareTo(to) > 0) ? alwaysFalse() : (from.equals(to) ? AggregationPredicates.eq(key, from) : this);
		}

		@Override
		public Set<String> getDimensions() {
			return singleton(key);
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return emptyMap();
		}

		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			Property property = property(record, key.replace('.', '$'));
			return Expressions.and(isNotNull(property, fields.get(key)),
					cmpGe(property, value(toInternalValue(fields, key, from))),
					cmpLe(property, value(toInternalValue(fields, key, to))));
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateBetween that = (PredicateBetween) o;

			if (!key.equals(that.key)) return false;
			if (!from.equals(that.from)) return false;
			return to.equals(that.to);

		}

		@Override
		public int hashCode() {
			int result = key.hashCode();
			result = 31 * result + from.hashCode();
			result = 31 * result + to.hashCode();
			return result;
		}

		@Override
		public String toString() {
			return "" + key + " BETWEEN " + from + " AND " + to;
		}
	}

	public static final class PredicateAnd implements AggregationPredicate {
		final List<AggregationPredicate> predicates;

		private PredicateAnd(List<AggregationPredicate> predicates) {
			this.predicates = predicates;
		}

		public List<AggregationPredicate> getPredicates() {
			return predicates;
		}

		@Override
		public AggregationPredicate simplify() {
			Set<AggregationPredicate> simplifiedPredicates = new LinkedHashSet<>();
			for (AggregationPredicate predicate : predicates) {
				AggregationPredicate simplified = predicate.simplify();
				if (simplified instanceof PredicateAnd) {
					simplifiedPredicates.addAll(((PredicateAnd) simplified).predicates);
				} else {
					simplifiedPredicates.add(simplified);
				}
			}
			boolean simplified;
			do {
				simplified = false;
				HashSet<AggregationPredicate> newPredicates = new HashSet<>();
				L:
				for (AggregationPredicate newPredicate : simplifiedPredicates) {
					for (AggregationPredicate simplifiedPredicate : newPredicates) {
						AggregationPredicate maybeSimplified = simplifyAnd(newPredicate, simplifiedPredicate);
						if (maybeSimplified != null) {
							newPredicates.remove(simplifiedPredicate);
							newPredicates.add(maybeSimplified);
							simplified = true;
							continue L;
						}
					}
					newPredicates.add(newPredicate);
				}
				simplifiedPredicates = newPredicates;
			} while (simplified);

			return simplifiedPredicates.isEmpty() ?
					alwaysTrue() :
					simplifiedPredicates.size() == 1 ?
							first(simplifiedPredicates) :
							and(new ArrayList<>(simplifiedPredicates));
		}

		@Override
		public Set<String> getDimensions() {
			Set<String> result = new HashSet<>();
			for (AggregationPredicate predicate : predicates) {
				result.addAll(predicate.getDimensions());
			}
			return result;
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			Map<String, Object> result = new HashMap<>();
			for (AggregationPredicate predicate : predicates) {
				result.putAll(predicate.getFullySpecifiedDimensions());
			}
			return result;
		}

		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			List<PredicateDef> predicateDefs = new ArrayList<>();
			for (AggregationPredicate predicate : predicates) {
				predicateDefs.add(predicate.createPredicateDef(record, fields));
			}
			return Expressions.and(predicateDefs);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateAnd that = (PredicateAnd) o;

			return new HashSet<>(predicates).equals(new HashSet<>(that.predicates));

		}

		@Override
		public int hashCode() {
			return new HashSet<>(predicates).hashCode();
		}

		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner(" AND ");
			for (AggregationPredicate predicate : predicates) joiner.add(predicate != null ? predicate.toString() : null);

			return "(" + joiner.toString() + ")";
		}
	}

	public static final class PredicateOr implements AggregationPredicate {
		final List<AggregationPredicate> predicates;

		PredicateOr(List<AggregationPredicate> predicates) {
			this.predicates = predicates;
		}

		public List<AggregationPredicate> getPredicates() {
			return predicates;
		}

		@Override
		public AggregationPredicate simplify() {
			Set<AggregationPredicate> simplifiedPredicates = new LinkedHashSet<>();
			for (AggregationPredicate predicate : predicates) {
				AggregationPredicate simplified = predicate.simplify();
				if (simplified instanceof PredicateOr) {
					simplifiedPredicates.addAll(((PredicateOr) simplified).predicates);
				} else {
					simplifiedPredicates.add(simplified);
				}
			}
			return simplifiedPredicates.isEmpty() ?
					alwaysTrue() :
					simplifiedPredicates.size() == 1 ?
							first(simplifiedPredicates) :
							or(new ArrayList<>(simplifiedPredicates));
		}

		@Override
		public Set<String> getDimensions() {
			Set<String> result = new HashSet<>();
			for (AggregationPredicate predicate : predicates) {
				result.addAll(predicate.getDimensions());
			}
			return result;
		}

		@Override
		public Map<String, Object> getFullySpecifiedDimensions() {
			return emptyMap();
		}

		@Override
		public PredicateDef createPredicateDef(Expression record, Map<String, FieldType> fields) {
			List<PredicateDef> predicateDefs = new ArrayList<>();
			for (AggregationPredicate predicate : predicates) {
				predicateDefs.add(predicate.createPredicateDef(record, fields));
			}
			return Expressions.or(predicateDefs);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PredicateOr that = (PredicateOr) o;

			return new HashSet<>(predicates).equals(new HashSet<>(that.predicates));

		}

		@Override
		public int hashCode() {
			return new HashSet<>(predicates).hashCode();
		}

		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner(" OR ");
			for (AggregationPredicate predicate : predicates) joiner.add(predicate != null ? predicate.toString() : null);
			return "(" + joiner.toString() + ")";
		}
	}

	public static AggregationPredicate alwaysTrue() {
		return PredicateAlwaysTrue.instance;
	}

	public static AggregationPredicate alwaysFalse() {
		return PredicateAlwaysFalse.instance;
	}

	public static AggregationPredicate not(AggregationPredicate predicate) {
		return new PredicateNot(predicate);
	}

	public static AggregationPredicate and(List<AggregationPredicate> predicates) {
		return new PredicateAnd(predicates);
	}

	public static AggregationPredicate and(AggregationPredicate... predicates) {
		return and(asList(predicates));
	}

	public static AggregationPredicate or(List<AggregationPredicate> predicates) {
		return new PredicateOr(predicates);
	}

	public static AggregationPredicate or(AggregationPredicate... predicates) {
		return or(asList(predicates));
	}

	public static AggregationPredicate eq(String key, Object value) {
		return new PredicateEq(key, value);
	}

	public static AggregationPredicate notEq(String key, Object value) {
		return new PredicateNotEq(key, value);
	}

	public static AggregationPredicate ge(String key, Comparable value) {
		return new PredicateGe(key, value);
	}

	public static AggregationPredicate le(String key, Comparable value) {
		return new PredicateLe(key, value);
	}

	public static AggregationPredicate gt(String key, Comparable value) {
		return new PredicateGt(key, value);
	}

	public static AggregationPredicate lt(String key, Comparable value) {
		return new PredicateLt(key, value);
	}

	public static AggregationPredicate has(String key) {
		return new PredicateHas(key);
	}

	public static AggregationPredicate in(String key, Collection values) {
		return values.size() == 1 ? new PredicateEq(key, values.toArray()[0]) : new PredicateIn(key, new TreeSet(values));
	}

	public static AggregationPredicate in(String key, Comparable... values) {
		return values.length == 1 ? new PredicateEq(key, values[0]) : new PredicateIn(key, new TreeSet(asList(values)));
	}

	public static AggregationPredicate regexp(String key, String pattern) {
		return new PredicateRegexp(key, pattern);
	}

	public static AggregationPredicate between(String key, Comparable from, Comparable to) {
		return new PredicateBetween(key, from, to);
	}

	public static final class RangeScan {
		private final PrimaryKey from;
		private final PrimaryKey to;

		private RangeScan(PrimaryKey from, PrimaryKey to) {
			this.from = from;
			this.to = to;
		}

		public static RangeScan noScan() {
			return new RangeScan(null, null);
		}

		public static RangeScan fullScan() {
			return new RangeScan(PrimaryKey.ofArray(), PrimaryKey.ofArray());
		}

		public static RangeScan rangeScan(PrimaryKey from, PrimaryKey to) {
			return new RangeScan(from, to);
		}

		public boolean isNoScan() {
			return from == null;
		}

		public boolean isFullScan() {
			return from.size() == 0;
		}

		public boolean isRangeScan() {
			return !isNoScan() && !isFullScan();
		}

		public PrimaryKey getFrom() {
			checkState(!isNoScan());
			return from;
		}

		public PrimaryKey getTo() {
			checkState(!isNoScan());
			return to;
		}
	}

	private static PredicateDef isNotNull(Expression field, FieldType fieldType) {
		return (fieldType != null && fieldType.getInternalDataType().isPrimitive())
				? Expressions.alwaysTrue()
				: Expressions.isNotNull(field);
	}

	private static PredicateDef isNull(Expression field, FieldType fieldType) {
		return (fieldType != null && fieldType.getInternalDataType().isPrimitive())
				? Expressions.alwaysFalse()
				: Expressions.isNull(field);
	}

	private static Object toInternalValue(Map<String, FieldType> fields, String key, Object value) {
		return fields.containsKey(key) ? fields.get(key).toInternalValue(value) : value;
	}

	public static RangeScan toRangeScan(AggregationPredicate predicate, List<String> primaryKey, Map<String, FieldType> fields) {
		predicate = predicate.simplify();
		if (predicate == alwaysFalse())
			return RangeScan.noScan();
		List<AggregationPredicate> conjunctions = new ArrayList<>();
		if (predicate instanceof PredicateAnd) {
			conjunctions.addAll(((PredicateAnd) predicate).predicates);
		} else {
			conjunctions.add(predicate);
		}

		List<Object> from = new ArrayList<>();
		List<Object> to = new ArrayList<>();

		L:
		for (String key : primaryKey) {
			for (int j = 0; j < conjunctions.size(); j++) {
				AggregationPredicate conjunction = conjunctions.get(j);
				if (conjunction instanceof PredicateEq && ((PredicateEq) conjunction).key.equals(key)) {
					conjunctions.remove(j);
					PredicateEq eq = (PredicateEq) conjunction;
					from.add(toInternalValue(fields, eq.key, eq.value));
					to.add(toInternalValue(fields, eq.key, eq.value));
					continue L;
				}
				if (conjunction instanceof PredicateBetween && ((PredicateBetween) conjunction).key.equals(key)) {
					conjunctions.remove(j);
					PredicateBetween between = (PredicateBetween) conjunction;
					from.add(toInternalValue(fields, between.key, between.from));
					to.add(toInternalValue(fields, between.key, between.to));
					break L;
				}
			}
			break;
		}

		return RangeScan.rangeScan(PrimaryKey.ofList(from), PrimaryKey.ofList(to));
	}

}
