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

import java.time.Instant;

public class TripDocument extends Document {
	public String location;
	public String geohash;
	public String hotspot;
	public long pickupCount;
	public long avgTripDuration;
	public long sumTripDuration;
	public long avgTripDistance;
	public long sumTripDistance;
	public double avgTripSpeed;

	public TripDocument() {
		super(Instant.EPOCH);

		this.location = "";
		this.geohash = "";
		this.hotspot = "";
		this.pickupCount = 0;
		this.avgTripDuration = 0;
		this.sumTripDuration = 0;
		this.avgTripDistance = 0;
		this.sumTripDistance = 0;
		this.avgTripSpeed = 0;
	}
}
