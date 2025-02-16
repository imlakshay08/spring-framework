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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.annotation.SynthesizedAnnotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests for {@link ReflectiveRuntimeHintsRegistrar}.
 *
 * @author Stephane Nicoll
 */
class ReflectiveRuntimeHintsRegistrarTests {

	private final ReflectiveRuntimeHintsRegistrar registrar = new ReflectiveRuntimeHintsRegistrar();

	private final RuntimeHints runtimeHints = new RuntimeHints();

	@Test
	void shouldIgnoreNonAnnotatedType() {
		RuntimeHints mock = mock(RuntimeHints.class);
		this.registrar.registerRuntimeHints(mock, String.class);
		verifyNoInteractions(mock);
	}

	@Test
	void shouldProcessAnnotationOnType() {
		process(SampleTypeAnnotatedBean.class);
		assertThat(this.runtimeHints.reflection().getTypeHint(SampleTypeAnnotatedBean.class))
				.isNotNull();
	}

	@Test
	void shouldProcessAnnotationOnConstructor() {
		process(SampleConstructorAnnotatedBean.class);
		assertThat(this.runtimeHints.reflection().getTypeHint(SampleConstructorAnnotatedBean.class))
				.satisfies(typeHint -> assertThat(typeHint.constructors()).singleElement()
						.satisfies(constructorHint -> assertThat(constructorHint.getParameterTypes())
								.containsExactly(TypeReference.of(String.class))));
	}

	@Test
	void shouldProcessAnnotationOnField() {
		process(SampleFieldAnnotatedBean.class);
		assertThat(this.runtimeHints.reflection().getTypeHint(SampleFieldAnnotatedBean.class))
				.satisfies(typeHint -> assertThat(typeHint.fields()).singleElement()
						.satisfies(fieldHint -> assertThat(fieldHint.getName()).isEqualTo("managed")));
	}

	@Test
	void shouldProcessAnnotationOnMethod() {
		process(SampleMethodAnnotatedBean.class);
		assertThat(this.runtimeHints.reflection().getTypeHint(SampleMethodAnnotatedBean.class))
				.satisfies(typeHint -> assertThat(typeHint.methods()).singleElement()
						.satisfies(methodHint -> assertThat(methodHint.getName()).isEqualTo("managed")));
	}

	@Test
	void shouldNotRegisterAnnotationProxyIfNotNeeded() {
		process(SampleMethodMetaAnnotatedBean.class);
		RuntimeHints runtimeHints = this.runtimeHints;
		assertThat(runtimeHints.proxies().jdkProxies()).isEmpty();
	}

	@Test
	void shouldRegisterAnnotationProxy() {
		process(SampleMethodMetaAnnotatedBeanWithAlias.class);
		RuntimeHints runtimeHints = this.runtimeHints;
		assertThat(RuntimeHintsPredicates.proxies().forInterfaces(
				SampleInvoker.class, SynthesizedAnnotation.class)).accepts(runtimeHints);
	}

	@Test
	void shouldProcessAnnotationOnInterface() {
		process(SampleMethodAnnotatedBeanWithInterface.class);
		assertThat(this.runtimeHints.reflection().getTypeHint(SampleInterface.class))
				.satisfies(typeHint -> assertThat(typeHint.methods()).singleElement()
						.satisfies(methodHint -> assertThat(methodHint.getName()).isEqualTo("managed")));
		assertThat(this.runtimeHints.reflection().getTypeHint(SampleMethodAnnotatedBeanWithInterface.class))
				.satisfies(typeHint -> assertThat(typeHint.methods()).singleElement()
						.satisfies(methodHint -> assertThat(methodHint.getName()).isEqualTo("managed")));
	}

	@Test
	void shouldProcessAnnotationOnInheritedClass() {
		process(SampleMethodAnnotatedBeanWithInheritance.class);
		assertThat(this.runtimeHints.reflection().getTypeHint(SampleInheritedClass.class))
				.satisfies(typeHint -> assertThat(typeHint.methods()).singleElement()
						.satisfies(methodHint -> assertThat(methodHint.getName()).isEqualTo("managed")));
		assertThat(this.runtimeHints.reflection().getTypeHint(SampleMethodAnnotatedBeanWithInheritance.class))
				.satisfies(typeHint -> assertThat(typeHint.methods()).singleElement()
						.satisfies(methodHint -> assertThat(methodHint.getName()).isEqualTo("managed")));
	}

	private void process(Class<?> beanClass) {
		this.registrar.registerRuntimeHints(this.runtimeHints, beanClass);
	}

	@Reflective
	@SuppressWarnings("unused")
	static class SampleTypeAnnotatedBean {

		private String notManaged;

		public void notManaged() {

		}
	}

	@SuppressWarnings("unused")
	static class SampleConstructorAnnotatedBean {

		@Reflective
		SampleConstructorAnnotatedBean(String name) {

		}

		SampleConstructorAnnotatedBean(Integer nameAsNumber) {

		}

	}

	@SuppressWarnings("unused")
	static class SampleFieldAnnotatedBean {

		@Reflective
		String managed;

		String notManaged;

	}

	@SuppressWarnings("unused")
	static class SampleMethodAnnotatedBean {

		@Reflective
		void managed() {
		}

		void notManaged() {
		}

	}

	@SuppressWarnings("unused")
	static class SampleMethodMetaAnnotatedBean {

		@SampleInvoker
		void invoke() {
		}

		void notManaged() {
		}

	}

	@SuppressWarnings("unused")
	static class SampleMethodMetaAnnotatedBeanWithAlias {

		@RetryInvoker
		void invoke() {
		}

		void notManaged() {
		}

	}

	static class SampleMethodAnnotatedBeanWithInterface implements SampleInterface {

		@Override
		public void managed() {
		}

		public void notManaged() {
		}

	}

	static class SampleMethodAnnotatedBeanWithInheritance extends SampleInheritedClass {

		@Override
		public void managed() {
		}

		public void notManaged() {
		}

	}

	@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
	@Retention(RetentionPolicy.RUNTIME)
	@Documented
	@Reflective
	@interface SampleInvoker {

		int retries() default 0;

	}

	@Target({ ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	@Documented
	@SampleInvoker
	@interface RetryInvoker {

		@AliasFor(attribute = "retries", annotation = SampleInvoker.class)
		int value() default 1;

	}

	interface SampleInterface {

		@Reflective
		void managed();
	}

	static class SampleInheritedClass {

		@Reflective
		void managed() {
		}
	}

}
