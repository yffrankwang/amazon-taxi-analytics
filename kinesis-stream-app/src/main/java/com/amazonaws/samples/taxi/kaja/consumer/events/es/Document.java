/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may
 * not use this file except in compliance with the License. A copy of the
 * License is located at
 *
 *    http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazonaws.samples.taxi.kaja.consumer.events.es;

import java.lang.reflect.Type;
import java.time.Instant;

import org.apache.commons.lang3.time.FastDateFormat;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public abstract class Document {
	public final static FastDateFormat TIMESTAMP_FMT = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss");
	
	private static final Gson gson = new GsonBuilder()
		.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
		.registerTypeAdapter(Instant.class, new JsonSerializer<Instant>() {
			@Override
			public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
				return new JsonPrimitive(TIMESTAMP_FMT.format(src.toEpochMilli()));
			}
		})
		.create();

	public Instant timestamp;

	protected Document(Instant timestamp) {
		this.timestamp = timestamp;
	}

	@Override
	public String toString() {
		return gson.toJson(this);
	}
}
