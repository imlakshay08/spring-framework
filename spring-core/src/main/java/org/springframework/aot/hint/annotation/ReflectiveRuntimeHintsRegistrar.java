/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aot.hint.annotation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.support.RuntimeHintsUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Process {@link Reflective} annotated elements.
 *
 * @author Stephane Nicoll
 * since 6.0
 */
public class ReflectiveRuntimeHintsRegistrar {

	private final Map<Class<? extends ReflectiveProcessor>, ReflectiveProcessor> processors = new HashMap<>();


	/**
	 * Register the relevant runtime hints for elements that are annotated with
	 * {@link Reflective}.
	 * @param runtimeHints the runtime hints instance to use
	 * @param types the types to process
	 */
	public void registerRuntimeHints(RuntimeHints runtimeHints, Class<?>... types) {
		Set<Entry> entries = new HashSet<>();
		Arrays.stream(types).forEach(type -> {
			processType(entries, type);
			for (Class<?> implementedInterface : ClassUtils.getAllInterfacesForClass(type)) {
				processType(entries, implementedInterface);
			}
		});
		entries.forEach(entry -> {
			AnnotatedElement element = entry.element();
			entry.processor().registerReflectionHints(runtimeHints.reflection(), element);
			registerAnnotationIfNecessary(runtimeHints, element);
		});
	}

	private void registerAnnotationIfNecessary(RuntimeHints hints, AnnotatedElement element) {
		MergedAnnotation<Reflective> reflectiveAnnotation = MergedAnnotations.from(element)
				.get(Reflective.class);
		MergedAnnotation<?> metaSource = reflectiveAnnotation.getMetaSource();
		if (metaSource != null) {
			RuntimeHintsUtils.registerAnnotationIfNecessary(hints, metaSource);
		}
	}

	private void processType(Set<Entry> entries, Class<?> typeToProcess) {
		if (isReflective(typeToProcess)) {
			entries.add(createEntry(typeToProcess));
		}
		doWithReflectiveConstructors(typeToProcess, constructor ->
				entries.add(createEntry(constructor)));
		ReflectionUtils.doWithFields(typeToProcess, field ->
				entries.add(createEntry(field)), this::isReflective);
		ReflectionUtils.doWithMethods(typeToProcess, method ->
				entries.add(createEntry(method)), this::isReflective);
	}

	private void doWithReflectiveConstructors(Class<?> typeToProcess, Consumer<Constructor<?>> consumer) {
		for (Constructor<?> constructor : typeToProcess.getDeclaredConstructors()) {
			if (isReflective(constructor)) {
				consumer.accept(constructor);
			}
		}
	}

	private boolean isReflective(AnnotatedElement element) {
		return MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY).isPresent(Reflective.class);
	}

	@SuppressWarnings("unchecked")
	private Entry createEntry(AnnotatedElement element) {
		Class<? extends ReflectiveProcessor>[] processorClasses = (Class<? extends ReflectiveProcessor>[])
				MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY).get(Reflective.class).getClassArray("value");
		List<ReflectiveProcessor> processors = Arrays.stream(processorClasses).distinct()
				.map(processorClass -> this.processors.computeIfAbsent(processorClass, this::instantiateClass))
				.toList();
		ReflectiveProcessor processorToUse = (processors.size() == 1 ? processors.get(0)
				: new DelegateReflectiveProcessor(processors));
		return new Entry(element, processorToUse);
	}

	private ReflectiveProcessor instantiateClass(Class<? extends ReflectiveProcessor> type) {
		try {
			return type.getDeclaredConstructor().newInstance();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to instantiate " + type, ex);
		}
	}

	static class DelegateReflectiveProcessor implements ReflectiveProcessor {

		private final Iterable<ReflectiveProcessor> processors;

		public DelegateReflectiveProcessor(Iterable<ReflectiveProcessor> processors) {
			this.processors = processors;
		}

		@Override
		public void registerReflectionHints(ReflectionHints hints, AnnotatedElement element) {
			this.processors.forEach(processor -> processor.registerReflectionHints(hints, element));
		}

	}

	private record Entry(AnnotatedElement element, ReflectiveProcessor processor) {}

}
