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

package org.springframework.web.reactive.function.client;

import java.io.IOException;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.ObservationConvention;

import org.springframework.http.client.observation.ClientHttpObservation;
import org.springframework.http.observation.HttpOutcome;
import org.springframework.util.StringUtils;

/**
 * Default implementation for a {@code WebClient} {@link ObservationConvention},
 * extracting information from the {@link ClientObservationContext}.
 *
 * @author Brian Clozel
 * @since 6.0
 */
public class DefaultClientObservationConvention implements ClientObservationConvention {

	private static final String DEFAULT_NAME = "http.client.requests";

	private static final KeyValue URI_NONE = KeyValue.of(ClientObservation.LowCardinalityKeyNames.URI, "none");

	private static final KeyValue METHOD_NONE = KeyValue.of(ClientObservation.LowCardinalityKeyNames.METHOD, "none");

	private static final KeyValue STATUS_IO_ERROR = KeyValue.of(ClientHttpObservation.LowCardinalityKeyNames.STATUS, "IO_ERROR");

	private static final KeyValue STATUS_CLIENT_ERROR = KeyValue.of(ClientHttpObservation.LowCardinalityKeyNames.STATUS, "CLIENT_ERROR");

	private static final KeyValue EXCEPTION_NONE = KeyValue.of(ClientObservation.LowCardinalityKeyNames.EXCEPTION, "none");

	private static final KeyValue URI_EXPANDED_NONE = KeyValue.of(ClientHttpObservation.HighCardinalityKeyNames.URI_EXPANDED, "none");

	private static final KeyValue CLIENT_NAME_NONE = KeyValue.of(ClientHttpObservation.HighCardinalityKeyNames.CLIENT_NAME, "none");

	private final String name;


	/**
	 * Create a convention with the default name {@code "http.client.requests"}.
	 */
	public DefaultClientObservationConvention() {
		this(DEFAULT_NAME);
	}

	/**
	 * Create a convention with a custom name.
	 * @param name the observation name
	 */
	public DefaultClientObservationConvention(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getContextualName(ClientObservationContext context) {
		return "http " + context.getCarrier().method().name().toLowerCase();
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(ClientObservationContext context) {
		return KeyValues.of(uri(context), method(context), status(context), exception(context), outcome(context));
	}

	protected KeyValue uri(ClientObservationContext context) {
		if (context.getUriTemplate() != null) {
			return KeyValue.of(ClientObservation.LowCardinalityKeyNames.URI, context.getUriTemplate());
		}
		return URI_NONE;
	}

	protected KeyValue method(ClientObservationContext context) {
		if (context.getCarrier() != null) {
			return KeyValue.of(ClientObservation.LowCardinalityKeyNames.METHOD, context.getCarrier().method().name());
		}
		else {
			return METHOD_NONE;
		}
	}

	protected KeyValue status(ClientObservationContext context) {
		if (context.isAborted()) {
			return STATUS_CLIENT_ERROR;
		}
		ClientResponse response = context.getResponse();
		if (response != null) {
			return KeyValue.of(ClientObservation.LowCardinalityKeyNames.STATUS, String.valueOf(response.statusCode().value()));
		}
		if (context.getError().isPresent() && context.getError().get() instanceof IOException) {
			return STATUS_IO_ERROR;
		}
		return STATUS_CLIENT_ERROR;
	}

	protected KeyValue exception(ClientObservationContext context) {
		return context.getError().map(exception -> {
			String simpleName = exception.getClass().getSimpleName();
			return KeyValue.of(ClientObservation.LowCardinalityKeyNames.EXCEPTION,
					StringUtils.hasText(simpleName) ? simpleName : exception.getClass().getName());
		}).orElse(EXCEPTION_NONE);
	}

	protected KeyValue outcome(ClientObservationContext context) {
		if (context.isAborted()) {
			return HttpOutcome.UNKNOWN.asKeyValue();
		}
		if (context.getResponse() != null) {
			HttpOutcome httpOutcome = HttpOutcome.forStatus(context.getResponse().statusCode());
			return httpOutcome.asKeyValue();
		}
		return HttpOutcome.UNKNOWN.asKeyValue();
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(ClientObservationContext context) {
		return KeyValues.of(uriExpanded(context), clientName(context));
	}

	protected KeyValue uriExpanded(ClientObservationContext context) {
		if (context.getCarrier() != null) {
			return KeyValue.of(ClientObservation.HighCardinalityKeyNames.URI_EXPANDED, context.getCarrier().url().toASCIIString());
		}
		return URI_EXPANDED_NONE;
	}

	protected KeyValue clientName(ClientObservationContext context) {
		if (context.getCarrier() != null && context.getCarrier().url().getHost() != null) {
			return KeyValue.of(ClientObservation.HighCardinalityKeyNames.CLIENT_NAME, context.getCarrier().url().getHost());
		}
		return CLIENT_NAME_NONE;
	}

}
