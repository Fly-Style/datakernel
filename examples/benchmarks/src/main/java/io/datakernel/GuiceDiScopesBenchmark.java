package io.datakernel;

import com.google.inject.*;
import org.openjdk.jmh.annotations.Scope;
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
 * Created on 24.07.19.
 */
@State(Scope.Benchmark)
public class GuiceDiScopesBenchmark {

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

        @Inject
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

        @Inject
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

        @Inject
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

        @Inject
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

        @Inject
        Cookie1(Pastry pastry) {
            this.pastry = pastry;
        }

        public Pastry getPastry() {
            return pastry;
        }
    }

    static class Cookie2 {
        private final Pastry pastry;

        @Inject
        Cookie2(Pastry pastry) {
            this.pastry = pastry;
        }

        public Pastry getPastry() {
            return pastry;
        }
    }

    static class Cookie3 {
        private final Pastry pastry;

        @Inject
        Cookie3(Pastry pastry) {
            this.pastry = pastry;
        }

        public Pastry getPastry() {
            return pastry;
        }
    }

    static class Cookie4 {
        private final Pastry pastry;

        @Inject
        Cookie4(Pastry pastry) {
            this.pastry = pastry;
        }

        public Pastry getPastry() {
            return pastry;
        }
    }

    static class Cookie5 {
        private final Pastry pastry;

        @Inject
        Cookie5(Pastry pastry) {
            this.pastry = pastry;
        }

        public Pastry getPastry() {
            return pastry;
        }
    }

    static class Cookie6 {
        private final Pastry pastry;

        @Inject
        Cookie6(Pastry pastry) {
            this.pastry = pastry;
        }

        public Pastry getPastry() {
            return pastry;
        }
    }

    static class Cookie7 {
        private final Pastry pastry;

        @Inject
        Cookie7(Pastry pastry) {
            this.pastry = pastry;
        }

        public Pastry getPastry() {
            return pastry;
        }
    }

    static class TORT {
        private final Cookie1 c1;
        private final Cookie2 c2;
        private final Cookie3 c3;
        private final Cookie4 c4;
        private final Cookie5 c5;
        private final Cookie6 c6;
        private final Cookie7 c7;

        public Cookie4 getC4() {
            return c4;
        }

        @Inject
        public TORT(Cookie1 c1, Cookie2 c2, Cookie3 c3, Cookie4 c4, Cookie5 c5, Cookie6 c6, Cookie7 c7) {
            this.c1 = c1;
            this.c2 = c2;
            this.c3 = c3;
            this.c4 = c4;
            this.c5 = c5;
            this.c6 = c6;
            this.c7 = c7;
        }
    }

    AbstractModule cookbook;
    Injector injector;

    Cookie1 cookie1;
    Cookie2 cookie2;
    Cookie3 cookie3;
    Cookie4 cookie4;
    Cookie5 cookie5;
    Cookie6 cookie6;
    Cookie7 cookie7;

    @Setup
    public void setup() {

        cookbook = new AbstractModule() {

            @Override
            public void configure() {
                SimpleScope orderScope = new SimpleScope();

                // tell Guice about the scope
                bindScope(GuiceOrder.class, orderScope);

                // make our scope instance injectable
                bind(SimpleScope.class)
                        .annotatedWith(GuiceOrder.class)
                        .toInstance(orderScope);
            }

            @Provides
            @Singleton
            Kitchen kitchen() { return new Kitchen(); }


            @Provides

            Sugar sugar() { return new Sugar("Sugarello", 10.f); }

            @Provides

            Butter butter() { return new Butter("Kyivmlyn", 20.0f); }

            @Provides

            Flour flour() { return new Flour("Kyivska", 100.0f); }

            @Provides

            Pastry pastry(Sugar sugar, Butter butter, Flour flour) {
                return new Pastry(sugar, butter, flour);
            }

            @Provides

            Cookie1 cookie1(Pastry pastry) {
                return new Cookie1(pastry);
            }

            @Provides

            Cookie2 cookie2(Pastry pastry) {
                return new Cookie2(pastry);
            }

            @Provides

            Cookie3 cookie3(Pastry pastry) {
                return new Cookie3(pastry);
            }

            @Provides

            Cookie4 cookie4(Pastry pastry) {
                return new Cookie4(pastry);
            }

            @Provides

            Cookie5 cookie5(Pastry pastry) {
                return new Cookie5(pastry);
            }

            @Provides

            Cookie6 cookie6(Pastry pastry) {
                return new Cookie6(pastry);
            }

            @Provides

            Cookie7 cookie7(Pastry pastry) {
                return new Cookie7(pastry);
            }

            @Provides

            TORT tort(Cookie1 c1, Cookie2 c2, Cookie3 c3, Cookie4 c4, Cookie5 c5, Cookie6 c6, Cookie7 c7) {
                return new TORT(c1, c2, c3, c4, c5, c6, c7);
            }

        };
        injector = Guice.createInjector(cookbook);
    }


    @Param({"0", "1", "10"})
    public int arg;

    @Benchmark
    @OutputTimeUnit(value = TimeUnit.NANOSECONDS)
    public void testMethod(Blackhole blackhole) {
        Kitchen kitchen = injector.getInstance(Kitchen.class);
        for (int i = 0; i < arg; ++i) {
            cookie1 = injector.getInstance(Cookie1.class);
            cookie2 = injector.getInstance(Cookie2.class);
            cookie3 = injector.getInstance(Cookie3.class);
            cookie4 = injector.getInstance(Cookie4.class);
            cookie5 = injector.getInstance(Cookie5.class);
            cookie6 = injector.getInstance(Cookie6.class);
            cookie7 = injector.getInstance(Cookie7.class);
            blackhole.consume(cookie1);
            blackhole.consume(cookie2);
            blackhole.consume(cookie3);
            blackhole.consume(cookie4);
            blackhole.consume(cookie5);
            blackhole.consume(cookie6);
            blackhole.consume(cookie7);
            blackhole.consume(kitchen);
        }

    }

	public static void main(String[] args) throws RunnerException {

		Options opt = new OptionsBuilder()
				.include(GuiceDiScopesBenchmark.class.getSimpleName())
				.forks(2)
				.warmupIterations(3)
				.warmupTime(TimeValue.seconds(1L))
				.measurementIterations(10)
				.measurementTime(TimeValue.seconds(2L))
				.mode(Mode.AverageTime)
				.timeUnit(TimeUnit.NANOSECONDS)
				.shouldDoGC(false)
				.build();

		new Runner(opt).run();
	}
}

