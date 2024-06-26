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

package org.springframework.web.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import org.springframework.web.testfixture.servlet.MockHttpServletRequest;
import org.springframework.web.testfixture.servlet.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultHttpRequestsObservationConvention}.
 * @author Brian Clozel
 */
class DefaultHttpRequestsObservationConventionTests {

	private final DefaultHttpRequestsObservationConvention convention = new DefaultHttpRequestsObservationConvention();

	private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/resource");

	private final MockHttpServletResponse response = new MockHttpServletResponse();

	private final HttpRequestsObservationContext context = new HttpRequestsObservationContext(this.request, this.response);


	@Test
	void shouldHaveDefaultName() {
		assertThat(convention.getName()).isEqualTo("http.server.requests");
	}

	@Test
	void shouldHaveContextualName() {
		assertThat(convention.getContextualName(this.context)).isEqualTo("http get");
	}

	@Test
	void supportsOnlyHttpRequestsObservationContext() {
		assertThat(this.convention.supportsContext(this.context)).isTrue();
		assertThat(this.convention.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void addsKeyValuesForExchange() {
		this.request.setMethod("POST");
		this.request.setRequestURI("/test/resource");
		this.request.setPathInfo("/test/resource");

		assertThat(this.convention.getLowCardinalityKeyValues(this.context)).hasSize(5)
				.contains(KeyValue.of("method", "POST"), KeyValue.of("uri", "UNKNOWN"), KeyValue.of("status", "200"),
						KeyValue.of("exception", "none"), KeyValue.of("outcome", "SUCCESS"));
		assertThat(this.convention.getHighCardinalityKeyValues(this.context)).hasSize(1)
				.contains(KeyValue.of("uri.expanded", "/test/resource"));
	}

	@Test
	void addsKeyValuesForExchangeWithPathPattern() {
		this.request.setRequestURI("/test/resource");
		this.request.setPathInfo("/test/resource");
		this.context.setPathPattern("/test/{name}");

		assertThat(this.convention.getLowCardinalityKeyValues(this.context)).hasSize(5)
				.contains(KeyValue.of("method", "GET"), KeyValue.of("uri", "/test/{name}"), KeyValue.of("status", "200"),
						KeyValue.of("exception", "none"), KeyValue.of("outcome", "SUCCESS"));
		assertThat(this.convention.getHighCardinalityKeyValues(this.context)).hasSize(1)
				.contains(KeyValue.of("uri.expanded", "/test/resource"));
	}

	@Test
	void addsKeyValuesForErrorExchange() {
		this.request.setRequestURI("/test/resource");
		this.request.setPathInfo("/test/resource");
		this.context.setError(new IllegalArgumentException("custom error"));
		this.response.setStatus(500);

		assertThat(this.convention.getLowCardinalityKeyValues(this.context)).hasSize(5)
				.contains(KeyValue.of("method", "GET"), KeyValue.of("uri", "UNKNOWN"), KeyValue.of("status", "500"),
						KeyValue.of("exception", "IllegalArgumentException"), KeyValue.of("outcome", "SERVER_ERROR"));
		assertThat(this.convention.getHighCardinalityKeyValues(this.context)).hasSize(1)
				.contains(KeyValue.of("uri.expanded", "/test/resource"));
	}

	@Test
	void addsKeyValuesForRedirectExchange() {
		this.request.setRequestURI("/test/redirect");
		this.request.setPathInfo("/test/redirect");
		this.response.setStatus(302);
		this.response.addHeader("Location", "https://example.org/other");

		assertThat(this.convention.getLowCardinalityKeyValues(this.context)).hasSize(5)
				.contains(KeyValue.of("method", "GET"), KeyValue.of("uri", "REDIRECTION"), KeyValue.of("status", "302"),
						KeyValue.of("exception", "none"), KeyValue.of("outcome", "REDIRECTION"));
		assertThat(this.convention.getHighCardinalityKeyValues(this.context)).hasSize(1)
				.contains(KeyValue.of("uri.expanded", "/test/redirect"));
	}

	@Test
	void addsKeyValuesForNotFoundExchange() {
		this.request.setRequestURI("/test/notFound");
		this.request.setPathInfo("/test/notFound");
		this.response.setStatus(404);

		assertThat(this.convention.getLowCardinalityKeyValues(this.context)).hasSize(5)
				.contains(KeyValue.of("method", "GET"), KeyValue.of("uri", "NOT_FOUND"), KeyValue.of("status", "404"),
						KeyValue.of("exception", "none"), KeyValue.of("outcome", "CLIENT_ERROR"));
		assertThat(this.convention.getHighCardinalityKeyValues(this.context)).hasSize(1)
				.contains(KeyValue.of("uri.expanded", "/test/notFound"));
	}

}
