/*
 * Copyright (C) 2015 SoftIndex LLC.
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

import org.junit.Test;

import static io.datakernel.aggregation.AggregationPredicates.*;
import static org.junit.Assert.assertEquals;

public class PredicatesTest {
	@Test
	public void testSimplify() throws Exception {
		assertEquals(alwaysFalse(), and(eq("publisher", 10), eq("publisher", 20)).simplify());
		assertEquals(eq("publisher", 10), and(eq("publisher", 10), not(not(eq("publisher", 10)))).simplify());
		assertEquals(eq("publisher", 20), and(alwaysTrue(), eq("publisher", 20)).simplify());
		assertEquals(alwaysFalse(), and(alwaysFalse(), eq("publisher", 20)).simplify());
		assertEquals(and(eq("date", 20160101), eq("publisher", 20)), and(eq("date", 20160101), eq("publisher", 20)).simplify());

		assertEquals(and(eq("date", 20160101), eq("publisher", 20)),
				and(not(not(and(not(not(eq("date", 20160101))), eq("publisher", 20)))), not(not(eq("publisher", 20)))).simplify());
		assertEquals(and(eq("date", 20160101), eq("publisher", 20)),
				and(and(not(not(eq("publisher", 20))), not(not(eq("date", 20160101)))), and(eq("date", 20160101), eq("publisher", 20))).simplify());
	}

	@Test
	public void testPredicateNotEqAndPredicateEqSimplification() {
		AggregationPredicate expected = eq("x", 10);
		AggregationPredicate actual = and(notEq("x", 12), eq("x", 10));
		AggregationPredicate actual2 = and(eq("x", 10), notEq("x", 12));
		assertEquals(expected, actual.simplify());
		// test symmetry
		assertEquals(expected, actual2.simplify());
	}

	@Test
	public void testPredicateNot_NegatesPredicateEqAndPredicateNotEqProperly() {
		assertEquals(eq("x", 10), not(notEq("x", 10)).simplify());
		assertEquals(notEq("x", 10), not(eq("x", 10)).simplify());
	}

	@Test
	public void testPredicateNotEqAndPredicateEq() {
		assertEquals(alwaysFalse(), and(eq("x", 10), notEq("x", 10)).simplify());
		assertEquals(alwaysFalse(), and(notEq("x", 10), eq("x", 10)).simplify());
	}

	@Test
	public void testUnnecessaryPredicates_areRemoved_whenSimplified() {
		AggregationPredicate predicate = and(
				not(eq("x", 1)),
				notEq("x", 1),
				not(not(not(eq("x", 1)))),
				eq("x", 2));
		AggregationPredicate expected = and(notEq("x", 1), eq("x", 2)).simplify();
		assertEquals(expected, predicate.simplify());
	}

	@Test
	public void testBetweenPredicateAndPredicateNotEq() {
		AggregationPredicate predicate;
		predicate = and(notEq("x", 6), between("x", 5, 10));
		assertEquals(predicate, predicate.simplify());

		predicate = and(notEq("x", 5), between("x", 5, 10));
		assertEquals(predicate, predicate.simplify());

		predicate = and(notEq("x", 10), between("x", 5, 10));
		assertEquals(predicate, predicate.simplify());

		predicate = and(notEq("x", 12), between("x", 5, 10));
		assertEquals(between("x", 5, 10), predicate.simplify());
	}

	@Test
	public void testPredicateGtAndPredicateGe() {
		AggregationPredicate predicate;
		predicate = and(ge("x", 10), gt("x", 10));
		assertEquals(gt("x", 10), predicate.simplify());

		predicate = and(gt("x", 10), ge("x", 10));
		assertEquals(gt("x", 10), predicate.simplify());

		predicate = and(gt("x", 11), ge("x", 10));
		assertEquals(gt("x", 11), predicate.simplify());

		predicate = and(ge("x", 11), gt("x", 10));
		assertEquals(ge("x", 11), predicate.simplify());

		predicate = and(ge("x", 10), gt("x", 11));
		assertEquals(gt("x", 11), predicate.simplify());
	}

	@Test
	public void testPredicateGeAndPredicateGe() {
		AggregationPredicate predicate;
		predicate = and(ge("x", 10), ge("x", 11));
		assertEquals(ge("x", 11), predicate.simplify());

		predicate = and(ge("x", 11), ge("x", 10));
		assertEquals(ge("x", 11), predicate.simplify());

		predicate = and(ge("x", 10), ge("x", 10));
		assertEquals(ge("x", 10), predicate.simplify());
	}

	@Test
	public void testPredicateGtAndPredicateGt() {
		AggregationPredicate predicate;
		predicate = and(gt("x", 10), gt("x", 11));
		assertEquals(gt("x", 11), predicate.simplify());

		predicate = and(gt("x", 11), gt("x", 10));
		assertEquals(gt("x", 11), predicate.simplify());

		predicate = and(gt("x", 10), gt("x", 10));
		assertEquals(gt("x", 10), predicate.simplify());
	}

	@Test
	public void testPredicateLeAndPredicateLe() {
		AggregationPredicate predicate;
		predicate = and(le("x", 10), le("x", 11));
		assertEquals(le("x", 10), predicate.simplify());

		predicate = and(le("x", 11), le("x", 10));
		assertEquals(le("x", 10), predicate.simplify());

		predicate = and(le("x", 10), le("x", 10));
		assertEquals(le("x", 10), predicate.simplify());
	}

	@Test
	public void testPredicateLeAndPredicateLt() {
		AggregationPredicate predicate;
		predicate = and(le("x", 11), lt("x", 11));
		assertEquals(lt("x", 11), predicate.simplify());

		predicate = and(le("x", 11), lt("x", 10));
		assertEquals(lt("x", 10), predicate.simplify());

		predicate = and(le("x", 10), lt("x", 11));
		assertEquals(le("x", 10), predicate.simplify());
	}

	@Test
	public void testPredicateGeAndPredicateLe() {
		AggregationPredicate predicate;
		predicate = and(ge("x", 11), le("x", 11));
		assertEquals(eq("x", 11), predicate.simplify());

		predicate = and(ge("x", 11), le("x", 10));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());

		predicate = and(ge("x", 10), le("x", 11));
		assertEquals(between("x", 10, 11), predicate.simplify());
	}

	@Test
	public void testPredicateLtAndPredicateLt() {
		AggregationPredicate predicate;
		predicate = and(lt("x", 11), lt("x", 11));
		assertEquals(lt("x", 11), predicate.simplify());

		predicate = and(lt("x", 11), lt("x", 10));
		assertEquals(lt("x", 10), predicate.simplify());

		predicate = and(lt("x", 10), lt("x", 11));
		assertEquals(lt("x", 10), predicate.simplify());
	}

	@Test
	public void testPredicateLtAndPredicateGe() {
		AggregationPredicate predicate;
		predicate = and(lt("x", 11), ge("x", 11));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());

		predicate = and(lt("x", 10), ge("x", 11));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());

		predicate = and(lt("x", 11), ge("x", 10));
		assertEquals(predicate, predicate.simplify());
	}

	@Test
	public void testPredicateLeAndPredicateGt() {
		AggregationPredicate predicate;
		predicate = and(le("x", 11), gt("x", 11));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());

		predicate = and(le("x", 11), gt("x", 10));
		assertEquals(predicate, predicate.simplify());

		predicate = and(le("x", 10), gt("x", 11));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());
	}

	@Test
	public void testPredicateLtAndPredicateGt() {
		AggregationPredicate predicate;
		predicate = and(lt("x", 11), gt("x", 11));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());

		predicate = and(lt("x", 11), gt("x", 10));
		assertEquals(predicate, predicate.simplify());

		predicate = and(lt("x", 10), gt("x", 11));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());
	}

	@Test
	public void testPredicateBetweenAndPredicateLe() {
		AggregationPredicate predicate;
		predicate = and(between("x", -5, 5), le("x", -6));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());

		predicate = and(between("x", -5, 5), le("x", -5));
		assertEquals(eq("x", -5), predicate.simplify());

		predicate = and(between("x", -5, 5), le("x", 0));
		assertEquals(between("x", -5, 0), predicate.simplify());

		predicate = and(between("x", -5, 5), le("x", 5));
		assertEquals(between("x", -5, 5), predicate.simplify());

		predicate = and(between("x", -5, 5), le("x", 6));
		assertEquals(between("x", -5, 5), predicate.simplify());
	}

	@Test
	public void testPredicateBetweenAndPredicateLt() {
		AggregationPredicate predicate;
		predicate = and(between("x", -5, 5), lt("x", -6));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());

		predicate = and(between("x", -5, 5), lt("x", -5));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());

		predicate = and(between("x", -5, 5), lt("x", 0));
		assertEquals(predicate, predicate.simplify());

		predicate = and(between("x", -5, 5), lt("x", 5));
		assertEquals(predicate, predicate.simplify());

		predicate = and(between("x", -5, 5), lt("x", 6));
		assertEquals(between("x", -5, 5), predicate.simplify());
	}

	@Test
	public void testPredicateBetweenAndPredicateGe() {
		AggregationPredicate predicate;
		predicate = and(between("x", -5, 5), ge("x", -6));
		assertEquals(between("x", -5, 5), predicate.simplify());

		predicate = and(between("x", -5, 5), ge("x", -5));
		assertEquals(between("x", -5, 5), predicate.simplify());

		predicate = and(between("x", -5, 5), ge("x", 0));
		assertEquals(between("x", 0, 5), predicate.simplify());

		predicate = and(between("x", -5, 5), ge("x", 5));
		assertEquals(eq("x", 5), predicate.simplify());

		predicate = and(between("x", -5, 5), ge("x", 6));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());
	}

	@Test
	public void testPredicateBetweenAndPredicateGt() {
		AggregationPredicate predicate;
		predicate = and(between("x", -5, 5), gt("x", -6));
		assertEquals(between("x", -5, 5), predicate.simplify());

		predicate = and(between("x", -5, 5), gt("x", -5));
		assertEquals(predicate, predicate.simplify());

		predicate = and(between("x", -5, 5), gt("x", 0));
		assertEquals(predicate, predicate.simplify());

		predicate = and(between("x", -5, 5), gt("x", 5));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());

		predicate = and(between("x", -5, 5), gt("x", 6));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());
	}

	@Test
	public void testPredicateBetweenAndPredicateBetween() {
		AggregationPredicate predicate;
		predicate = and(between("x", -5, 5), between("x", 5, 10));
		assertEquals(eq("x", 5), predicate.simplify());

		predicate = and(between("x", -5, 5), between("x", 4, 6));
		assertEquals(between("x", 4, 5), predicate.simplify());

		predicate = and(between("x", -5, 5), between("x", -6, 6));
		assertEquals(between("x", -5, 5), predicate.simplify());

		predicate = and(between("x", -5, 5), between("x", 6, 7));
		assertEquals(AggregationPredicates.alwaysFalse(), predicate.simplify());
	}

	@Test
	public void testPredicateNotEqAndPredicateLe() {
		AggregationPredicate predicate;
		predicate = and(notEq("x", -5), le("x", -4));
		assertEquals(predicate, predicate.simplify());

		predicate = and(notEq("x", -5), le("x", -5));
		assertEquals(lt("x", -5), predicate.simplify());

		predicate = and(notEq("x", -5), le("x", -6));
		assertEquals(le("x", -6), predicate.simplify());
	}

	@Test
	public void testPredicateNotEqAndPredicateLt() {
		AggregationPredicate predicate;
		predicate = and(notEq("x", -5), lt("x", -4));
		assertEquals(predicate, predicate.simplify());

		predicate = and(notEq("x", -5), lt("x", -5));
		assertEquals(lt("x", -5), predicate.simplify());

		predicate = and(notEq("x", -5), lt("x", -6));
		assertEquals(lt("x", -6), predicate.simplify());
	}

	@Test
	public void testPredicateNotEqAndPredicateGe() {
		AggregationPredicate predicate;
		predicate = and(notEq("x", -5), ge("x", -4));
		assertEquals(ge("x", -4), predicate.simplify());

		predicate = and(notEq("x", -5), ge("x", -5));
		assertEquals(gt("x", -5), predicate.simplify());

		predicate = and(notEq("x", -5), ge("x", -6));
		assertEquals(predicate, predicate.simplify());
	}

	@Test
	public void testPredicateNotEqAndPredicateGt() {
		AggregationPredicate predicate;
		predicate = and(notEq("x", -5), gt("x", -4));
		assertEquals(gt("x", -4), predicate.simplify());

		predicate = and(notEq("x", -5), gt("x", -5));
		assertEquals(gt("x", -5), predicate.simplify());

		predicate = and(notEq("x", -5), gt("x", -6));
		assertEquals(predicate, predicate.simplify());
	}

	@Test
	public void testPredicateEqAndPredicateLe() {
		AggregationPredicate predicate;
		predicate = and(eq("x", -5), le("x", -4));
		assertEquals(eq("x", -5), predicate.simplify());

		predicate = and(eq("x", -5), le("x", -5));
		assertEquals(eq("x", -5), predicate.simplify());

		predicate = and(eq("x", -5), le("x", -6));
		assertEquals(alwaysFalse(), predicate.simplify());
	}

	@Test
	public void testPredicateEqAndPredicateLt() {
		AggregationPredicate predicate;
		predicate = and(eq("x", -5), lt("x", -4));
		assertEquals(eq("x", -5), predicate.simplify());

		predicate = and(eq("x", -5), lt("x", -5));
		assertEquals(alwaysFalse(), predicate.simplify());

		predicate = and(eq("x", -5), lt("x", -6));
		assertEquals(alwaysFalse(), predicate.simplify());
	}

	@Test
	public void testPredicateEqAndPredicateGe() {
		AggregationPredicate predicate;
		predicate = and(eq("x", -5), ge("x", -4));
		assertEquals(alwaysFalse(), predicate.simplify());

		predicate = and(eq("x", -5), ge("x", -5));
		assertEquals(eq("x", -5), predicate.simplify());

		predicate = and(eq("x", -5), ge("x", -6));
		assertEquals(eq("x", -5), predicate.simplify());
	}

	@Test
	public void testPredicateEqAndPredicateGt() {
		AggregationPredicate predicate;
		predicate = and(eq("x", -5), gt("x", -4));
		assertEquals(alwaysFalse(), predicate.simplify());

		predicate = and(eq("x", -5), gt("x", -5));
		assertEquals(alwaysFalse(), predicate.simplify());

		predicate = and(eq("x", -5), gt("x", -6));
		assertEquals(eq("x", -5), predicate.simplify());
	}

	@Test
	public void testPredicateNotEqAndPredicateNotEq() {
		AggregationPredicate predicate;
		predicate = and(notEq("x", -5), notEq("x", -5));
		assertEquals(notEq("x", -5), predicate.simplify());

		predicate = and(notEq("x", -5), notEq("x", -6));
		assertEquals(predicate, predicate.simplify());
	}

	@Test
	public void testPredicateInAndPredicateEq() {
		AggregationPredicate predicate;
		predicate = and(in("x", 1, 2, 3, 5), eq("x", 2));
		assertEquals(eq("x", 2), predicate.simplify());

		predicate = and(in("x", 1, 2, 3, 5), eq("x", 4));
		assertEquals(alwaysFalse(), predicate.simplify());

		predicate = and(in("x", 1), eq("x", 1));
		assertEquals(eq("x", 1), predicate.simplify());
	}

	@Test
	public void testPredicateInAndPredicateIn() {
		AggregationPredicate predicate;
		predicate = and(in("x", 1, 2, 3, 5), in("x", 2));
		assertEquals(eq("x", 2), predicate.simplify());

		predicate = and(in("x", 1, 2, 3, 5), in("x", 4));
		assertEquals(alwaysFalse(), predicate.simplify());

		predicate = and(in("x", 1, 3, 5, 7), in("x", 2, 4, 6, 8));
		assertEquals(alwaysFalse(), predicate.simplify());

		predicate = and(in("x", 1), in("x", 2));
		assertEquals(alwaysFalse(), predicate.simplify());

		predicate = and(in("x", 1, 2), in("x", -1, -2));
		assertEquals(alwaysFalse(), predicate.simplify());

		predicate = and(in("x", 1), in("x", 1, 2));
		assertEquals(eq("x", 1), predicate.simplify());
	}

	@Test
	public void testPredicateInAndPredicateLe() {
		AggregationPredicate predicate;
		predicate = and(in("x", 1, 2, 3, 5), le("x", 2));
		assertEquals(in("x", 1, 2), predicate.simplify());

		predicate = and(in("x", 1), le("x", 2));
		assertEquals(eq("x", 1), predicate.simplify());

		predicate = and(in("x", 1, 2), le("x", 2));
		assertEquals(in("x", 1, 2), predicate.simplify());

		predicate = and(in("x", 1, 2, 3), le("x", 2));
		assertEquals(in("x", 1, 2), predicate.simplify());

		predicate = and(in("x", 1, 2, 3), le("x", 0));
		assertEquals(alwaysFalse(), predicate.simplify());
	}

	@Test
	public void testPredicateInAndPredicateLt() {
		AggregationPredicate predicate;
		predicate = and(in("x", 1, 2, 3, 5), lt("x", 2));
		assertEquals(in("x", 1), predicate.simplify());

		predicate = and(in("x", 1), lt("x", 2));
		assertEquals(eq("x", 1), predicate.simplify());

		predicate = and(in("x", 1, 2), lt("x", 2));
		assertEquals(eq("x", 1), predicate.simplify());

		predicate = and(in("x", 1, 2, 4), lt("x", 3));
		assertEquals(in("x", 1, 2), predicate.simplify());

		predicate = and(in("x", 1, 2, 3), lt("x", 0));
		assertEquals(alwaysFalse(), predicate.simplify());
	}

	@Test
	public void testPredicateInAndPredicateGe() {
		AggregationPredicate predicate;
		predicate = and(in("x", 1, 2, 3, 5), ge("x", 2));
		assertEquals(in("x", 2, 3, 5), predicate.simplify());

		predicate = and(in("x", 1), ge("x", 2));
		assertEquals(alwaysFalse(), predicate.simplify());

		predicate = and(in("x", 1, 2), ge("x", 2));
		assertEquals(eq("x", 2), predicate.simplify());

		predicate = and(in("x", 1, 2, 3), ge("x", 1));
		assertEquals(in("x", 1, 2, 3), predicate.simplify());

		predicate = and(in("x", 1, 2, 3), ge("x", 0));
		assertEquals(in("x", 1, 2, 3), predicate.simplify());

		predicate = and(in("x", 1, 2, 3), ge("x", 4));
		assertEquals(alwaysFalse(), predicate.simplify());
	}

	@Test
	public void testPredicateInAndPredicateGt() {
		AggregationPredicate predicate;
		predicate = and(in("x", 1, 2, 3, 5), gt("x", 2));
		assertEquals(in("x", 3, 5), predicate.simplify());

		predicate = and(in("x", 1), gt("x", 2));
		assertEquals(alwaysFalse(), predicate.simplify());

		predicate = and(in("x", 1, 2), gt("x", 2));
		assertEquals(alwaysFalse(), predicate.simplify());

		predicate = and(in("x", 1, 2, 3), gt("x", 0));
		assertEquals(in("x", 1, 2, 3), predicate.simplify());
	}

}