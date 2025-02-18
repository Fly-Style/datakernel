package io.datakernel.di;

import io.datakernel.OrderScope;
import io.datakernel.di.annotation.Inject;
import io.datakernel.di.core.Injector;
import io.datakernel.di.core.Key;
import io.datakernel.di.module.AbstractModule;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

/**
 * @author is Alex Syrotenko (@pantokrator)
 * Created on 26.07.19.
 * @since 3.0.0
 */
@State(Scope.Benchmark)
public class DkDirectScopebindBenchmark {

	static class Kitchen {
		private final int places;

		@Inject
		Kitchen() {
			this.places = 1;
		}

		public int getPlaces() {
			return places;
		}
	}

	static class Sugar {
		private final String name;
		private final float weight;

		public Sugar() {
			this.name = "Sugarella";
			this.weight = 10.f;
		}
		//[END REGION_8]

		public Sugar(String name, float weight) {
			this.name = name;
			this.weight = weight;
		}

		public String getName() {
			return name;
		}

		public float getWeight() {
			return weight;
		}
	}

	static class Butter {
		private float weight;
		private String name;

		public Butter() {
			this.weight = 10.f;
			this.name = "Butter";
		}

		public Butter(String name, float weight) {
			this.weight = weight;
			this.name = name;
		}

		public float getWeight() {
			return weight;
		}

		public String getName() {
			return name;
		}
	}

	static class Flour {
		private float weight;
		private String name;

		public Flour() { }

		public Flour(String name, float weight) {
			this.weight = weight;
			this.name = name;
		}

		public float getWeight() {
			return weight;
		}

		public String getName() {
			return name;
		}
	}

	static class Pastry {
		private final Sugar sugar;
		private final Butter butter;
		private final Flour flour;

		Pastry(Sugar sugar, Butter butter, Flour flour) {
			this.sugar = sugar;
			this.butter = butter;
			this.flour = flour;
		}

		public Flour getFlour() {
			return flour;
		}

		public Sugar getSugar() {
			return sugar;
		}

		public Butter getButter() {
			return butter;
		}
	}

	static class Cookie1 {
		private final Pastry pastry;

		Cookie1(Pastry pastry) {
			this.pastry = pastry;
		}

		public Pastry getPastry() {
			return pastry;
		}
	}

	static class Cookie2 {
		private final Pastry pastry;

		Cookie2(Pastry pastry) {
			this.pastry = pastry;
		}

		public Pastry getPastry() {
			return pastry;
		}
	}

	static class Cookie3 {
		private final Pastry pastry;

		Cookie3(Pastry pastry) {
			this.pastry = pastry;
		}

		public Pastry getPastry() {
			return pastry;
		}
	}

	static class Cookie4 {
		private final Pastry pastry;

		Cookie4(Pastry pastry) {
			this.pastry = pastry;
		}

		public Pastry getPastry() {
			return pastry;
		}
	}

	static class Cookie5 {
		private final Pastry pastry;

		Cookie5(Pastry pastry) {
			this.pastry = pastry;
		}

		public Pastry getPastry() {
			return pastry;
		}
	}

	static class Cookie6 {
		private final Pastry pastry;

		Cookie6(Pastry pastry) {
			this.pastry = pastry;
		}

		public Pastry getPastry() {
			return pastry;
		}
	}

	static class CookieBucket {
		private final Cookie1 c1;
		private final Cookie2 c2;
		private final Cookie3 c3;
		private final Cookie4 c4;
		private final Cookie5 c5;
		private final Cookie6 c6;

		public Cookie4 getC4() {
			return c4;
		}

		CookieBucket(Cookie1 c1, Cookie2 c2, Cookie3 c3, Cookie4 c4, Cookie5 c5, Cookie6 c6) {
			this.c1 = c1;
			this.c2 = c2;
			this.c3 = c3;
			this.c4 = c4;
			this.c5 = c5;
			this.c6 = c6;
		}
	}

	AbstractModule cookbook;
	Injector injector;

	public static final io.datakernel.di.core.Scope ORDER_SCOPE = io.datakernel.di.core.Scope.of(OrderScope.class);

	@Setup
	public void setup() {
		cookbook = new AbstractModule() {
			@Override
			protected void configure() {
				bind(Kitchen.class).to(Kitchen::new);
				bind(Sugar.class).to(() -> new Sugar("Sugarello", 10.f)).in(OrderScope.class);
				bind(Butter.class).to(() -> new Butter("Kyivmlyn", 20.0f)).in(OrderScope.class);
				bind(Flour.class).to(() -> new Flour("Kyivska", 100.0f)).in(OrderScope.class);
				bind(Pastry.class).to(Pastry::new, Sugar.class, Butter.class, Flour.class).in(OrderScope.class);
				bind(Cookie1.class).to(Cookie1::new, Pastry.class).in(OrderScope.class);
				bind(Cookie2.class).to(Cookie2::new, Pastry.class).in(OrderScope.class);
				bind(Cookie3.class).to(Cookie3::new, Pastry.class).in(OrderScope.class);
				bind(Cookie4.class).to(Cookie4::new, Pastry.class).in(OrderScope.class);
				bind(Cookie5.class).to(Cookie5::new, Pastry.class).in(OrderScope.class);
				bind(Cookie6.class).to(Cookie6::new, Pastry.class).in(OrderScope.class);
				bind(CookieBucket.class).to(CookieBucket::new, Cookie1.class, Cookie2.class,
						Cookie3.class, Cookie4.class, Cookie5.class, Cookie6.class).in(OrderScope.class);
			}

		};

		injector = Injector.of(cookbook);

	}

	CookieBucket cb;
	Key<CookieBucket> key = Key.of(CookieBucket.class);

	@Param({"0", "1", "10"})
	int arg;

	@Benchmark
	@OutputTimeUnit(value = TimeUnit.NANOSECONDS)
	public void testMethod(Blackhole blackhole) {
		Kitchen kitchen = injector.getInstance(Kitchen.class);
		for (int i = 0; i < arg; ++i) {
			Injector subinjector = injector.enterScope(ORDER_SCOPE);
			cb = subinjector.getInstance(key);
			blackhole.consume(cb);
		}
		blackhole.consume(kitchen);
	}

	public static void main(String[] args) throws RunnerException {

		Options opt = new OptionsBuilder()
				.include(DkDirectScopebindBenchmark.class.getSimpleName())
				.forks(2)
				.warmupIterations(3)
				.warmupTime(TimeValue.seconds(1L))
				.measurementIterations(10)
				.measurementTime(TimeValue.seconds(2L))
				.mode(Mode.AverageTime)
				.timeUnit(TimeUnit.NANOSECONDS)
				.build();

		new Runner(opt).run();
	}
}


