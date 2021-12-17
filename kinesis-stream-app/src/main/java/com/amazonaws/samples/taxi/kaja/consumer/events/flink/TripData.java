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

package com.amazonaws.samples.taxi.kaja.consumer.events.flink;

public class TripData {
	public final String location;
	public final String geohash;
	public final String hotspot;
	public final long tripDuration;
	public final long tripDistance;

	public TripData(String location, String geohash, String hotspot, long tripDuration, long tripDistance) {
		this.location = location;
		this.geohash = geohash;
		this.hotspot = hotspot;
		this.tripDuration = tripDuration;
		this.tripDistance = tripDistance;
	}
}
