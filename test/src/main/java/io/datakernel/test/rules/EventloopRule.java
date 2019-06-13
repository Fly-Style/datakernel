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

package io.datakernel.test.rules;

import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.FatalErrorHandlers;
import io.datakernel.logger.LoggerConfigurer;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.logging.Level;

/**
 * {@link TestRule} that creates an eventloop and sets it to ThreadLocal
 */
public final class EventloopRule implements TestRule {

	static {
		createEventloop();
		LoggerConfigurer.enableLogging(Eventloop.class, Level.WARNING);
	}

	private static void createEventloop() {
		Eventloop.create()
				.withCurrentThread()
				.withFatalErrorHandler(FatalErrorHandlers.rethrowOnAnyError());
	}

	@Override
	public Statement apply(Statement base, Description description) {
		if (!Eventloop.getCurrentEventloop().inEventloopThread()) {
			createEventloop();
		}
		return base;
	}
}
