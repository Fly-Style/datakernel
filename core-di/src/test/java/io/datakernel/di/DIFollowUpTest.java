package io.datakernel.di;

import io.datakernel.di.annotation.Inject;
import io.datakernel.di.annotation.Named;
import io.datakernel.di.annotation.Provides;
import io.datakernel.di.core.Binding;
import io.datakernel.di.core.Injector;
import io.datakernel.di.core.Key;
import io.datakernel.di.core.Scope;
import io.datakernel.di.module.AbstractModule;
import io.datakernel.di.util.Trie;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotSame;

/**
 * @author is Alex Syrotenko (@pantokrator)
 * Created on 11.07.19.
 */
public class DIFollowUpTest {

	public static final Scope ORDER_SCOPE = Scope.of(Order.class);

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

	static class Cookie {
		private final Pastry pastry;

		@Inject
		Cookie(Pastry pastry) {
			this.pastry = pastry;
		}

		public Pastry getPastry() {
			return pastry;
		}
	}

	/**
	 *
	 * There is a kitchen, where anyone (maybe you) can automaGically
	 * create a tasty cookies with our wonderful DI baker (library).
	 *
	 **/

	/**
	 * Hardcore way to bake a @Cookie using DI.
	 * DI has so-called Binding structure within,
	 * where all keys (@dependencies) and their assembly recipes (@factory) are collected.
	 * You can use an ordered map of @Key? to @Bindings? for to describe the cooking actions sequence.
	 *
	 * You have @Sugar, @Butter and @Flour and you know how to bake a cookie.
	 *
	 * First of all, we should order an instances of this ingredients .
	 * So, we store this actions in our action map @bindings. (line 2-4)
	 *
	 * Afterwards, there is a recipe with description of cooking @Pastry using @Sugar, @Butter and @Flour,
	 * and you want to use this recipe with existing components.
	 * So, just store recipe and indicate necessary components (@Sugar, @Butter and @Flour) (line 5)
	 *
	 * After this operation you will have a @Pastry and you want to bake a @Cookie!
	 * Recipe says us that @Pastry is an only component for @Cookie baking.
	 * Same operation : store recipe and with indicated @Pastry component. (line 6)
	 *
	 * Before, you described a sequence of actions for our DI baker.
	 * It's baking time right now!
	 * Just call an injector for baking launch (line 8)
	 * and wait for your @Cookie instance! (line 9)
	 *
	 * Hard-mode baking end, well done!
	 */
	@Test
	public void manualBindSnippet() {
		Map<Key<?>, Binding<?>> bindings = new LinkedHashMap<>();
		bindings.put(Key.of(Sugar.class), Binding.to(() -> new Sugar("Sugarello", 10.0f)));
		bindings.put(Key.of(Butter.class), Binding.to(() -> new Butter("Kyivmlyn", 20.0f)));
		bindings.put(Key.of(Flour.class), Binding.to(() -> new Flour("Kyivska", 100.0f)));
		bindings.put(Key.of(Pastry.class), Binding.to(Pastry::new, Sugar.class, Butter.class, Flour.class));
		bindings.put(Key.of(Cookie.class), Binding.to(Cookie::new, Pastry.class));

		Injector injector = Injector.of(Trie.leaf(bindings));
		Cookie instance = injector.getInstance(Cookie.class);

		assertEquals(10.f, instance.getPastry().getSugar().getWeight());
	}

	@Test
	public void moduleBindSnippet() {
		AbstractModule module = new AbstractModule() {{
			bind(Sugar.class).to(() -> new Sugar("Sugarello", 10.0f));
			bind(Butter.class).to(() -> new Butter("Kyivmlyn", 20.0f));
			bind(Flour.class).to(() -> new Flour("Kyivska", 100.0f));
			bind(Pastry.class).to(Pastry::new, Sugar.class, Butter.class, Flour.class);
			bind(Cookie.class).to(Cookie::new, Pastry.class);
		}};

		Injector injector = Injector.of(module);
		assertEquals("Kyivmlyn", injector.getInstance(Cookie.class).getPastry().getButter().getName());
	}

	@Test
	public void provideAnnotationSnippet() {
		AbstractModule cookbook = new AbstractModule() {
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
			Cookie cookie(Pastry pastry) {
				return new Cookie(pastry);
			}
		};

		Injector injector = Injector.of(cookbook);
		assertEquals("Kyivmlyn", injector.getInstance(Cookie.class).getPastry().getButter().getName());
	}

	@Test
	public void injectAnnotationSnippet() {
		AbstractModule cookbook = new AbstractModule() {{
			bind(Cookie.class);
		}};

		Injector injector = Injector.of(cookbook);
		assertEquals("Sugarella", injector.getInstance(Cookie.class).getPastry().getSugar().getName());
	}

	@Test
	public void namedAnnotationSnippet() {
		AbstractModule cookbook = new AbstractModule() {
			@Provides
			@Named("zerosugar")
			Sugar sugar1() { return new Sugar("SugarFree", 0.f); }

			@Provides
			@Named("normal")
			Sugar sugar2() { return new Sugar("Sugarello", 10.f); }

			@Provides
			Butter butter() { return new Butter("Kyivmlyn", 20.f); }

			@Provides
			Flour flour() { return new Flour("Kyivska", 100.f); }

			@Provides
			@Named("normal")
			Pastry pastry1(@Named("normal") Sugar sugar, Butter butter, Flour flour) {
				return new Pastry(sugar, butter, flour);
			}

			@Provides
			@Named("zerosugar")
			Pastry pastry2(@Named("zerosugar") Sugar sugar, Butter butter, Flour flour) {
				return new Pastry(sugar, butter, flour);
			}

			@Provides
			@Named("normal")
			Cookie cookie1(@Named("normal") Pastry pastry) {
				return new Cookie(pastry);
			}

			@Provides
			@Named("zerosugar")
			Cookie cookie2(@Named("zerosugar") Pastry pastry) { return new Cookie(pastry); }
		};

		Injector injector = Injector.of(cookbook);

		float normalWeight = injector.getInstance(Key.of(Cookie.class, "normal"))
							         .getPastry().getSugar().getWeight();
		float zerosugarWeight = injector.getInstance(Key.of(Cookie.class, "zerosugar"))
									    .getPastry().getSugar().getWeight();

		assertEquals(10.f, normalWeight);
		assertEquals(0.f, zerosugarWeight);
	}

	@Test
	public void orderAnnotationSnippet() {
		AbstractModule cookbook = new AbstractModule() {

			@Provides
			Kitchen kitchen() { return new Kitchen(); }

			@Provides
			@Order
			Sugar sugar() { return new Sugar("Sugarello", 10.f); }

			@Provides
			@Order
			Butter butter() { return new Butter("Kyivmlyn", 20.0f); }

			@Provides
			@Order
			Flour flour() { return new Flour("Kyivska", 100.0f); }

			@Provides
			@Order
			Pastry pastry(Sugar sugar, Butter butter, Flour flour) {
				return new Pastry(sugar, butter, flour);
			}

			@Provides
			@Order
			Cookie cookie(Pastry pastry) {
				return new Cookie(pastry);
			}
		};

		Injector injector = Injector.of(cookbook);
		Kitchen kitchen = injector.getInstance(Kitchen.class);
		List<Cookie> cookies = new ArrayList<>();
		for (int i = 0; i < 10; ++i) {
			Injector subinjector = injector.enterScope(ORDER_SCOPE);

			assertEquals(injector.getInstance(Kitchen.class), kitchen);
			if (i > 0) assertNotSame(cookies.get(i - 1), subinjector.getInstance(Cookie.class));

			cookies.add(subinjector.getInstance(Cookie.class));
		}
		assertEquals(10, cookies.size());
	}

	@Test
	public void transformBindingSnippet() {
		AbstractModule cookbook = new AbstractModule() {

			@Override
			protected void configure() {
				transform(0, ((provider, scope, key, binding) -> {
					System.out.println(Instant.now().toString());
					return binding;
				}));
			}

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
			Cookie cookie(Pastry pastry) {
				return new Cookie(pastry);
			}
		};

		Injector injector = Injector.of(cookbook);
		assertEquals("Kyivska", injector.getInstance(Cookie.class).getPastry().getFlour().getName());
	}
}
