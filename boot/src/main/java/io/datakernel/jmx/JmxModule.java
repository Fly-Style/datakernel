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

package io.datakernel.jmx;

import com.google.inject.*;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.spi.ProvisionListener;
import io.datakernel.worker.WorkerPoolModule;

import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.datakernel.util.Preconditions.checkArgument;

public final class JmxModule extends AbstractModule {
	public static final double REFRESH_PERIOD_DEFAULT = 1.0;
	public static final int MAX_JMX_REFRESHES_PER_ONE_CYCLE_DEFAULT = 50;

	private final Set<Key<?>> singletonKeys = new HashSet<>();
	private final Set<Key<?>> workerKeys = new HashSet<>();

	private final Map<Key<?>, MBeanSettings> keyToSettings = new HashMap<>();
	private final Map<Type, MBeanSettings> typeToSettings = new HashMap<>();

	private double refreshPeriod = REFRESH_PERIOD_DEFAULT;
	private int maxJmxRefreshesPerOneCycle = MAX_JMX_REFRESHES_PER_ONE_CYCLE_DEFAULT;
	private final Map<Type, String> globalMBeans = new HashMap<>();

	private JmxModule() {}

	public static JmxModule create() {return new JmxModule();}

	public JmxModule withRefreshPeriod(double refreshPeriod) {
		checkArgument(refreshPeriod > 0.0);

		this.refreshPeriod = refreshPeriod;
		return this;
	}

	public JmxModule withMaxJmxRefreshesPerOneCycle(int max) {
		checkArgument(max > 0);

		this.maxJmxRefreshesPerOneCycle = max;
		return this;
	}

	public <T> JmxModule withModifier(Key<?> key, String attrName, AttributeModifier<T> modifier) {
		ensureSettings(key).addModifier(attrName, modifier);
		return this;
	}

	public <T> JmxModule withModifier(Type type, String attrName, AttributeModifier<T> modifier) {
		ensureSettings(type).addModifier(attrName, modifier);
		return this;
	}

	public JmxModule withOptional(Key<?> key, String attrName) {
		ensureSettings(key).addIncludedOptional(attrName);
		return this;
	}

	public JmxModule withOptional(Type type, String attrName) {
		ensureSettings(type).addIncludedOptional(attrName);
		return this;
	}

	public JmxModule withHistogram(Key<?> key, String attrName, int[] histogramLevels) {
		return this
				.withOptional(key, attrName + "_histogram")
				.withModifier(key, attrName, new AttributeModifier<ValueStats>() {
					@Override
					public void apply(ValueStats attribute) {
						attribute.setHistogramLevels(histogramLevels);
					}
				});
	}

	public JmxModule withHistogram(Class<?> clazz, String attrName, int[] histogramLevels) {
		return withHistogram(Key.get(clazz), attrName, histogramLevels);
	}

	public JmxModule withGlobalMBean(Type type, String name) {
		checkArgument(!globalMBeans.containsKey(type), "GlobalMBean for \"%s\" was already specified", type);

		globalMBeans.put(type, name);
		return this;
	}

	private MBeanSettings ensureSettings(Key<?> key) {
		MBeanSettings settings = keyToSettings.get(key);
		if (settings == null) {
			settings = MBeanSettings.defaultSettings();
			keyToSettings.put(key, settings);
		}
		return settings;
	}

	private MBeanSettings ensureSettings(Type key) {
		MBeanSettings settings = typeToSettings.get(key);
		if (settings == null) {
			settings = MBeanSettings.defaultSettings();
			typeToSettings.put(key, settings);
		}
		return settings;
	}

	@Override
	protected void configure() {
		bindListener(new AbstractMatcher<Binding<?>>() {
			@Override
			public boolean matches(Binding<?> binding) {
				return WorkerPoolModule.isWorkerScope(binding);
			}
		}, new ProvisionListener() {
			@Override
			public <T> void onProvision(ProvisionInvocation<T> provision) {
				synchronized (JmxModule.this) {
					if (provision.provision() != null) {
						workerKeys.add(provision.getBinding().getKey());
					}
				}
			}
		});
		bindListener(new AbstractMatcher<Binding<?>>() {
			@Override
			public boolean matches(Binding<?> binding) {
				return isSingleton(binding);
			}
		}, new ProvisionListener() {
			@Override
			public <T> void onProvision(ProvisionInvocation<T> provision) {
				synchronized (JmxModule.this) {
					if (provision.provision() != null) {
						singletonKeys.add(provision.getBinding().getKey());
					}
				}
			}
		});
	}

	private static boolean isSingleton(Binding<?> binding) {
		return binding.acceptScopingVisitor(new BindingScopingVisitor<Boolean>() {
			@Override
			public Boolean visitNoScoping() {
				return false;
			}

			@Override
			public Boolean visitScopeAnnotation(Class<? extends Annotation> visitedAnnotation) {
				return visitedAnnotation.equals(Singleton.class);
			}

			@Override
			public Boolean visitScope(Scope visitedScope) {
				return visitedScope.equals(Scopes.SINGLETON);
			}

			@Override
			public Boolean visitEagerSingleton() {
				return true;
			}
		});
	}

	@Provides
	DynamicMBeanFactory mbeanFactory() {
		return JmxMBeans.factory(refreshPeriod, maxJmxRefreshesPerOneCycle);
	}

	@Provides
	JmxRegistrator jmxRegistrator(Injector injector, JmxRegistry jmxRegistry, DynamicMBeanFactory mbeanFactory) {
		return JmxRegistrator.create(injector, singletonKeys, workerKeys, jmxRegistry, mbeanFactory,
				keyToSettings, typeToSettings, globalMBeans);
	}

	@Provides
	@Singleton
	JmxRegistry jmxRegistry(DynamicMBeanFactory mbeanFactory) {
		return JmxRegistry.create(
				ManagementFactory.getPlatformMBeanServer(),
				mbeanFactory
		).withRefreshPeriod(refreshPeriod);
	}
}
