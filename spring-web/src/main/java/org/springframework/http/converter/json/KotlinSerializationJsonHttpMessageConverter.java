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

package org.springframework.http.converter.json;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import kotlinx.serialization.KSerializer;
import kotlinx.serialization.SerializationException;
import kotlinx.serialization.SerializersKt;
import kotlinx.serialization.descriptors.PolymorphicKind;
import kotlinx.serialization.descriptors.SerialDescriptor;
import kotlinx.serialization.json.Json;

import org.springframework.core.GenericTypeResolver;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StreamUtils;

/**
 * Implementation of {@link org.springframework.http.converter.HttpMessageConverter}
 * that can read and write JSON using
 * <a href="https://github.com/Kotlin/kotlinx.serialization">kotlinx.serialization</a>.
 *
 * <p>This converter can be used to bind {@code @Serializable} Kotlin classes,
 * <a href="https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md#open-polymorphism">open polymorphic serialization</a>
 * is not supported.
 * It supports {@code application/json} and {@code application/*+json} with
 * various character sets, {@code UTF-8} being the default.
 *
 * @author Andreas Ahlenstorf
 * @author Sebastien Deleuze
 * @author Juergen Hoeller
 * @since 5.3
 */
public class KotlinSerializationJsonHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {

	private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

	private static final Map<Type, KSerializer<Object>> serializerCache = new ConcurrentReferenceHashMap<>();

	private final Json json;


	/**
	 * Construct a new {@code KotlinSerializationJsonHttpMessageConverter} with the default configuration.
	 */
	public KotlinSerializationJsonHttpMessageConverter() {
		this(Json.Default);
	}

	/**
	 * Construct a new {@code KotlinSerializationJsonHttpMessageConverter} with a custom configuration.
	 */
	public KotlinSerializationJsonHttpMessageConverter(Json json) {
		super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
		this.json = json;
	}


	@Override
	protected boolean supports(Class<?> clazz) {
		return serializer(clazz) != null;
	}

	@Override
	public boolean canRead(Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType) {
		return serializer(GenericTypeResolver.resolveType(type, contextClass)) != null && canRead(mediaType);
	}

	@Override
	public boolean canWrite(@Nullable Type type, Class<?> clazz, @Nullable MediaType mediaType) {
		return serializer(type != null ? GenericTypeResolver.resolveType(type, clazz) : clazz) != null && canWrite(mediaType);
	}

	@Override
	public final Object read(Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {
		KSerializer<Object> serializer = serializer(GenericTypeResolver.resolveType(type, contextClass));
		Assert.notNull(serializer, "The serializer should not be null");
		return decode(serializer, inputMessage);
	}

	@Override
	protected final Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {
		KSerializer<Object> serializer = serializer(clazz);
		Assert.notNull(serializer, "The serializer should not be null");
		return decode(serializer, inputMessage);
	}

	private Object decode(KSerializer<Object> serializer, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {

		MediaType contentType = inputMessage.getHeaders().getContentType();
		String jsonText = StreamUtils.copyToString(inputMessage.getBody(), getCharsetToUse(contentType));
		try {
			// TODO Use stream based API when available
			return this.json.decodeFromString(serializer, jsonText);
		}
		catch (SerializationException ex) {
			throw new HttpMessageNotReadableException("Could not read JSON: " + ex.getMessage(), ex, inputMessage);
		}
	}

	@Override
	protected final void writeInternal(Object object, @Nullable Type type, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {
		KSerializer<Object> serializer =  serializer(type != null ? type : object.getClass());
		Assert.notNull(serializer, "The serializer should not be null");
		encode(object, serializer, outputMessage);
	}

	private void encode(Object object, KSerializer<Object> serializer, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {

		try {
			String json = this.json.encodeToString(serializer, object);
			MediaType contentType = outputMessage.getHeaders().getContentType();
			outputMessage.getBody().write(json.getBytes(getCharsetToUse(contentType)));
			outputMessage.getBody().flush();
		}
		catch (IOException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new HttpMessageNotWritableException("Could not write JSON: " + ex.getMessage(), ex);
		}
	}

	private Charset getCharsetToUse(@Nullable MediaType contentType) {
		if (contentType != null && contentType.getCharset() != null) {
			return contentType.getCharset();
		}
		return DEFAULT_CHARSET;
	}

	/**
	 * Tries to find a serializer that can marshall or unmarshall instances of the given type
	 * using kotlinx.serialization. If no serializer can be found, an exception is thrown.
	 * <p>Resolved serializers are cached and cached results are returned on successive calls.
	 * @param type the type to find a serializer for
	 * @return a resolved serializer for the given type or {@code null} if no serializer
	 * supporting the given type can be found
	 */
	@Nullable
	private KSerializer<Object> serializer(Type type) {
		KSerializer<Object> serializer = serializerCache.get(type);
		if (serializer == null) {
			try {
				serializer = SerializersKt.serializerOrNull(type);
			}
			catch (IllegalArgumentException ignored) {
			}
			if (serializer == null || hasPolymorphism(serializer.getDescriptor(), new HashSet<>())) {
				return null;
			}
			serializerCache.put(type, serializer);
		}
		return serializer;
	}

	private boolean hasPolymorphism(SerialDescriptor descriptor, Set<String> alreadyProcessed) {
		alreadyProcessed.add(descriptor.getSerialName());
		if (descriptor.getKind().equals(PolymorphicKind.OPEN.INSTANCE)) {
			return true;
		}
		for (int i = 0 ; i < descriptor.getElementsCount() ; i++) {
			SerialDescriptor elementDescriptor = descriptor.getElementDescriptor(i);
			if (!alreadyProcessed.contains(elementDescriptor.getSerialName()) && hasPolymorphism(elementDescriptor, alreadyProcessed)) {
				return true;
			}
		}
		return false;
	}

}
