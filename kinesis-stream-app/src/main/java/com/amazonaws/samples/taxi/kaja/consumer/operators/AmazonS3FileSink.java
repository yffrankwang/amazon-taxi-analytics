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

package com.amazonaws.samples.taxi.kaja.consumer.operators;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.flink.api.common.serialization.Encoder;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;

import com.amazonaws.samples.taxi.kaja.consumer.events.es.TripDocument;

public class AmazonS3FileSink {
	public final static FastDateFormat TIMESTAMP_FMT = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");

	public static StreamingFileSink<TripDocument> buildS3FileSink(String s3SinkPath) {
		final StreamingFileSink<TripDocument> sink = StreamingFileSink
			.forRowFormat(new Path(s3SinkPath), new TripDocumentToCsvEncoder())
			.withBucketAssigner(new DateTimeBucketAssigner<TripDocument>("yyyyMMdd"))
			.withRollingPolicy(DefaultRollingPolicy.builder().build())
			.withOutputFileConfig(OutputFileConfig.builder().withPartSuffix(".csv").build())
			.build();

		return sink;
	}

	// format for forecast
	public static class TripDocumentToCsvEncoder implements Encoder<TripDocument> {
		private static final long serialVersionUID = 1L;

		@Override
		public void encode(TripDocument td, OutputStream stream) throws IOException {
			stream.write(TIMESTAMP_FMT.format(td.timestamp.toEpochMilli()).getBytes());
			stream.write(',');
			stream.write(td.geohash.getBytes());
			stream.write(',');
			stream.write(String.valueOf(td.pickupCount).getBytes());
			stream.write(',');
			stream.write(StringUtils.replaceChars(td.location, ',', '_').getBytes());
			stream.write('\n');
		}
	}
}
