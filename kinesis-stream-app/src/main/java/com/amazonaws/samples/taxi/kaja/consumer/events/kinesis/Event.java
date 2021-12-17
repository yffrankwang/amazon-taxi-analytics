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

package com.amazonaws.samples.taxi.kaja.consumer.events.kinesis;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Date;

import org.apache.commons.lang3.time.FastDateFormat;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;

public abstract class Event {
	public final static FastDateFormat FMT = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");

	private static final String TYPE_FIELD = "type";

	private static final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
		.registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>)(json, typeOfT, context) -> Instant.parse(json.getAsString()))
		.registerTypeAdapter(Date.class, (JsonDeserializer<Date>)(json, typeOfT, context) -> parseDate(json.getAsString()))
		.create();

	public static Date parseDate(String s) {
		try {
			return FMT.parse(s);
		} catch (Exception e) {
			return null;
		}
	}

	public static Event parseEvent(byte[] event) {
		// parse the event payload and remove the type attribute
		JsonReader jsonReader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(event)));
		JsonElement jsonElement = Streams.parse(jsonReader);
		JsonElement dataType = jsonElement.getAsJsonObject().remove(TYPE_FIELD);

		if (dataType == null) {
			throw new IllegalArgumentException("Event does not define a type field: " + new String(event));
		}

		// convert json to POJO, based on the type attribute
		switch (dataType.getAsString()) {
		case "watermark":
			return gson.fromJson(jsonElement, WatermarkEvent.class);
		case "trip":
		case "yellow":
		case "green":
		case "fhv":
		case "fhvhv":
			jsonElement.getAsJsonObject().addProperty("taxi_type", dataType.getAsString());
			TripEvent te = gson.fromJson(jsonElement, TripEvent.class);
			te.normalize();
			return te;
		default:
			throw new IllegalArgumentException("Found unsupported event type: " + dataType.getAsString());
		}
	}

	/**
	 * @return timestamp in epoch millies
	 */
	public abstract long getTimestamp();
}
