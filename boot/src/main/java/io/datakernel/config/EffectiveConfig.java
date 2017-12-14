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

package io.datakernel.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.*;

final class EffectiveConfig implements Config {
	private static final class CallsRegistry {
		final Map<String, String> calls = new HashMap<>();
		final Map<String, String> defaultCalls = new HashMap<>();
		final Map<String, String> all;

		CallsRegistry(Map<String, String> all) {
			this.all = all;
		}

		synchronized void registerCall(String root, String relativePath, String value) {
			calls.put(fullPath(root, relativePath), value);
		}

		synchronized void registerDefaultCall(String rootPath, String relativePath, String value, String defaultValue) {
			String fullPath = fullPath(rootPath, relativePath);
			calls.put(fullPath, value);
			defaultCalls.put(fullPath, defaultValue);
		}
	}

	private final Config config;
	private final String rootPath;
	private final CallsRegistry callsRegistry;

	// creators
	private EffectiveConfig(Config config, String rootPath, CallsRegistry callsRegistry) {
		this.config = config;
		this.rootPath = rootPath;
		this.callsRegistry = callsRegistry;
	}

	public static EffectiveConfig create(Config config) {
		Map<String, String> allProperties = new LinkedHashMap<>(); // same order as inserted in config
		fetchAllConfigs(config, THIS, allProperties);
		CallsRegistry callsRegistry = new CallsRegistry(allProperties);
		return new EffectiveConfig(config, THIS, callsRegistry);
	}

	private EffectiveConfig wrap(String path, Config config) {
		String newRootPath = fullPath(rootPath, path);
		return new EffectiveConfig(config, newRootPath, callsRegistry);
	}

	private static void fetchAllConfigs(Config config, String prefix, Map<String, String> container) {
		Set<String> children = config.getChildren();
		// we assume that each config must be either `leaf`(value, no children) or `branch`(no value, children)
		if (children.isEmpty()) {
			assert config.hasValue();
			container.put(prefix, config.get(THIS));
		} else {
			for (String childName : children) {
				Config childConfig = config.getChild(childName);
				String childPrefix = prefix.isEmpty() ? childName : prefix + "." + childName;
				fetchAllConfigs(childConfig, childPrefix, container);
			}
		}
	}

	// api config
	@Override
	public boolean hasValue() {
		return config.hasValue();
	}

	@Override
	public String get(String path) {
		String value = config.get(path);
		callsRegistry.registerCall(rootPath, path, value);
		return value;
	}

	@Override
	public String get(String path, String defaultValue) {
		String value = config.get(path, defaultValue);
		callsRegistry.registerDefaultCall(rootPath, path, value, defaultValue);
		return value;
	}

	@Override
	public <T> T get(ConfigConverter<T> converter, String path) {
		return converter.get(getChild(path));
	}

	@Override
	public <T> T get(ConfigConverter<T> converter, String path, T defaultValue) {
		return converter.get(getChild(path), defaultValue);
	}

	@Override
	public Config getChild(String path) {
		return wrap(path, config.getChild(path));
	}

	@Override
	public boolean hasChild(String path) {
		return config.hasChild(path);
	}

	@Override
	public Set<String> getChildren() {
		return config.getChildren();
	}

	@Override
	public int hashCode() {
		return config.hashCode();
	}

	@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
	@Override
	public boolean equals(Object obj) {
		return config.equals(obj);
	}

	@Override
	public String toString() {
		return config.toString();
	}

	// rendering
	void saveEffectiveConfig(Path outputPath) {
		try {
			String renderedConfig;
			synchronized (callsRegistry) {
				renderedConfig = render(this);
			}
			Files.write(outputPath, renderedConfig.getBytes(UTF_8), new StandardOpenOption[]{CREATE, WRITE, TRUNCATE_EXISTING});
		} catch (IOException e) {
			throw new RuntimeException("Failed to serialize effective config as properties file", e);
		}
	}

	static String render(EffectiveConfig config) throws IOException {
		CallsRegistry register = config.callsRegistry;
		StringBuilder sb = new StringBuilder();

		TreeSet<String> keys = new TreeSet<>();
		keys.addAll(register.calls.keySet());   // to get default
		keys.addAll(register.all.keySet());     // to get unused
		for (String key : keys) {
			String used = register.calls.get(key);
			String value = register.all.get(key);
			String defaultValue = register.defaultCalls.get(key);

			if (!register.calls.containsKey(key)) {
				sb.append("# UNUSED:  ");
				writeProperty(sb, key, value);
			} else {
				if (register.defaultCalls.containsKey(key) && (used == null || !used.equals(defaultValue))) {
					sb.append("# DEFAULT: ");
					writeProperty(sb, key, nullToEmpty(defaultValue));
				}
				if (used != null) {
					writeProperty(sb, key, used);
				}
			}
		}

		return sb.toString();
	}

	private static String nullToEmpty(String str) {
		return str == null ? "" : str;
	}

	private static void writeProperty(StringBuilder sb, String key, String value) {
		sb.append(encodeForPropertiesFile(key, true));
		sb.append(" = ");
		sb.append(encodeForPropertiesFile(value, false));
		sb.append("\n");
	}

	private static String encodeForPropertiesFile(String string, boolean escapeKey) {
		StringBuilder sb = new StringBuilder(string.length() * 2);

		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if ((c > 61) && (c < 127)) {
				if (c == '\\') {
					sb.append('\\');
					sb.append('\\');
					continue;
				}
				sb.append(c);
				continue;
			}
			switch (c) {
				case ' ':
					if (i == 0 || escapeKey)
						sb.append('\\');
					sb.append(' ');
					break;
				case '\t':
					sb.append('\\');
					sb.append('t');
					break;
				case '\n':
					sb.append('\\');
					sb.append('n');
					break;
				case '\r':
					sb.append('\\');
					sb.append('r');
					break;
				case '\f':
					sb.append('\\');
					sb.append('f');
					break;
				case '=':
				case ':':
				case '#':
				case '!':
					sb.append('\\');
					sb.append(c);
					break;
				default:
					sb.append(c);
			}
		}
		return sb.toString();
	}

	private static String fullPath(String rootPath, String relativePath) {
		return rootPath.isEmpty() || relativePath.isEmpty() ? rootPath + relativePath : rootPath + "." + relativePath;
	}
}
