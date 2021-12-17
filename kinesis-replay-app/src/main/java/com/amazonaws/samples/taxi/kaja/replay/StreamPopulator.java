/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.amazonaws.samples.taxi.kaja.replay;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.regions.Regions;
import com.amazonaws.samples.taxi.kaja.replay.events.JsonEvent;
import com.amazonaws.samples.taxi.kaja.replay.utils.BackpressureSemaphore;
import com.amazonaws.samples.taxi.kaja.replay.utils.EventBuffer;
import com.amazonaws.samples.taxi.kaja.replay.utils.EventReader;
import com.amazonaws.samples.taxi.kaja.replay.utils.WatermarkGenerator;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;
import com.amazonaws.services.kinesis.producer.UserRecordResult;
import com.google.common.util.concurrent.ListenableFuture;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;


public class StreamPopulator {
	private static final Logger LOG = LoggerFactory.getLogger(StreamPopulator.class);

	private static final String DEFAULT_REGION_NAME = Regions.getCurrentRegion()==null ? "us-east-1" : Regions.getCurrentRegion().getName();


	private final String streamName;
	private final String bucketName;
	private final String objectPrefix;
	private final long statisticsFrequencyMillies;
	private final EventBuffer eventBuffer;
	private final WatermarkGenerator watermarkGenerator;
	private final int maxOutstandingRecords;
	private final KinesisProducer kinesisProducer;
	private final BackpressureSemaphore<UserRecordResult> backpressureSemaphore;

	public StreamPopulator(String bucketRegion,
			String bucketName,
			String objectPrefix,
			String streamRegion,
			String streamName,
			boolean aggregate,
			String timestampAttributeName,
			float speedupFactor,
			long statisticsFrequencyMillies,
			boolean noWatermark,
			Instant seekToEpoch,
			int bufferSize,
			int maxOutstandingRecords,
			boolean noKinesis) {

		final S3Client s3 = S3Client.builder().region(Region.of(bucketRegion)).build();

		this.maxOutstandingRecords = maxOutstandingRecords;
		this.streamName = streamName;
		this.bucketName = bucketName;
		this.objectPrefix = objectPrefix;
		this.statisticsFrequencyMillies = statisticsFrequencyMillies;

		if (noKinesis) {
			this.kinesisProducer = null;
		} else {
			KinesisProducerConfiguration producerConfiguration = new KinesisProducerConfiguration()
				.setRegion(streamRegion)
				.setRecordTtl(60_000)
				.setThreadingModel(KinesisProducerConfiguration.ThreadingModel.POOLED)
				.setAggregationEnabled(aggregate);

			this.kinesisProducer = new KinesisProducer(producerConfiguration);
		}

		EventReader eventReader = new EventReader(s3, bucketName, objectPrefix, speedupFactor, timestampAttributeName);
		if (seekToEpoch != null) {
			eventReader.seek(seekToEpoch);
		}

		eventBuffer = new EventBuffer(eventReader, bufferSize);
		eventBuffer.start();

		if (!noWatermark) {
			watermarkGenerator = new WatermarkGenerator(Region.of(streamRegion), streamName);

			watermarkGenerator.start();
		} else {
			watermarkGenerator = null;
		}

		if (maxOutstandingRecords > 0) {
			this.backpressureSemaphore = new BackpressureSemaphore<>(maxOutstandingRecords);
		} else {
			this.backpressureSemaphore = null;
		}
	}


	private void populate() {
		long statisticsBatchEventCount = 0;

		try {
			LOG.info("populating internal event buffer");

			eventBuffer.fill();

			JsonEvent event = eventBuffer.peek();

			if (event == null) {
				LOG.error("didn't find any events to replay in s3://{}/{}", bucketName, objectPrefix);
				return;
			}

			LOG.info("starting to ingest events into stream {}", streamName);

			long lastStatisticsTime = System.currentTimeMillis();
			do {
				event = eventBuffer.take();

				Duration replayTimeGap = Duration.between(event.ingestionTime, Instant.now());

				if (replayTimeGap.isNegative()) {
					if (replayTimeGap.toMillis() < -30000) {
						LOG.info("sleep {} ms for ingestion time {} > {}", replayTimeGap, event.ingestionTime, Instant.now());
					}
					Thread.sleep(-replayTimeGap.toMillis());
				}

				ingestEvent(event);

				statisticsBatchEventCount++;

				// output statistics every statisticsFrequencyMillies ms
				if (System.currentTimeMillis() - lastStatisticsTime > statisticsFrequencyMillies) {
					double statisticsBatchEventRate = Math.round(1000.0 * statisticsBatchEventCount / statisticsFrequencyMillies);

					Instant watermarkTime;
					if (watermarkGenerator != null) {
						watermarkTime = watermarkGenerator.getMinWatermark();
					} else {
						watermarkTime = eventBuffer.peek().timestamp;
					}

					LOG.info("all events with timestamp until {} have been sent ({} events/sec, {} replay lag)",
							watermarkTime, statisticsBatchEventRate, Duration.ofSeconds(replayTimeGap.getSeconds()));

					statisticsBatchEventCount = 0;
					lastStatisticsTime = System.currentTimeMillis();
				}
			} while (eventBuffer.hasNext());

			LOG.info("all events have been sent");
		} catch (InterruptedException e) {
			LOG.warn("interrupted");
		} catch (Throwable e) {
			LOG.warn("unknown error", e);
		} finally {
			eventBuffer.interrupt();

			if (watermarkGenerator != null) {
				watermarkGenerator.interrupt();
			}

			if (kinesisProducer != null) {
				kinesisProducer.flushSync();
				kinesisProducer.destroy();
			}
		}

		LOG.info("populate complete");
	}

	private long traceCount;
	private void ingestEvent(JsonEvent event) {
		if (kinesisProducer == null) {
			traceCount++;
			LOG.info("[{}] add event: {}", traceCount, event.timestamp);
			return;
		}

		LOG.debug("add event to kinesis: {}", event);
		
		//queue the next event for ingestion to the Kinesis stream through the KPL
		ListenableFuture<UserRecordResult> f = kinesisProducer.addUserRecord(streamName, Integer.toString(event.hashCode()), event.toByteBuffer());

		if (watermarkGenerator != null) {
			//monitor if the event has actually been sent and adapt the largest possible watermark value accordingly
			watermarkGenerator.trackTimestamp(f, event);
		}

		if (backpressureSemaphore != null) {
			//block if too many events are buffered locally
			backpressureSemaphore.acquire(f);
		}

//		int outstandingRecords = kinesisProducer.getOutstandingRecordsCount();
//		if (maxOutstandingRecords > 0 && outstandingRecords > maxOutstandingRecords) {
//			int sec = maxOutstandingRecords / outstandingRecords;
//
//			LOG.debug("sleep {} seconds for too many events are buffered locally", sec);
//
//			try {
//				Thread.sleep(1000 * sec);
//			} catch (InterruptedException e) {
//			}
//		}
	}

	public static void main(String[] args) throws ParseException {
		Options options = new Options()
				.addOption("bucketRegion", true, "the region of the S3 bucket")
				.addOption("bucketName", true, "the bucket containing the raw event data")
				.addOption("objectPrefix", true, "the prefix of the objects containing the raw event data")
				.addOption("streamRegion", true, "the region of the Kinesis stream")
				.addOption("streamName", true, "the name of the kinesis stream the events are sent to")
				.addOption("speedup", true, "the speedup factor for replaying events into the kinesis stream")
				.addOption("timestampAttributeName", true, "the name of the attribute that contains the timestamp according to which events ingested into the stream")
				.addOption("aggregate", "turn on aggregation of multiple events into a single Kinesis record")
				.addOption("seek", true, "start replaying events at given timestamp")
				.addOption("statisticsFrequency", true, "print statistics every statisticFrequency ms")
				.addOption("noWatermark", "don't ingest watermarks into the stream")
				.addOption("bufferSize", true, "size of the buffer that holds events to sent to the stream")
				.addOption("maxOutstandingRecords", true, "block producer if more than maxOutstandingRecords are in flight")
				.addOption("noKinesis", "do not send to kinesis")
				.addOption("help", "print this help message");

		CommandLine line = new DefaultParser().parse(options, args);

		if (line.hasOption("help")) {
			new HelpFormatter().printHelp(MethodHandles.lookup().lookupClass().getName(), options);
			return;
		}

		Instant seekToEpoch = null;
		if (line.hasOption("seek")) {
			seekToEpoch = Instant.parse(line.getOptionValue("seek"));
		}

		StreamPopulator populator = new StreamPopulator(line.getOptionValue("bucketRegion", "us-east-1"),
			line.getOptionValue("bucketName", "nyc-tlc"),
			line.getOptionValue("objectPrefix", "trip data/"),
			line.getOptionValue("streamRegion", DEFAULT_REGION_NAME),
			line.getOptionValue("streamName", "taxi-trip-events"),
			line.hasOption("aggregate"),
			line.getOptionValue("timestampAttributeName", "pickup_datetime"),
			Float.parseFloat(line.getOptionValue("speedup", "3600")),
			Long.parseLong(line.getOptionValue("statisticsFrequency", "20000")),
			line.hasOption("noWatermark"),
			seekToEpoch,
			Integer.parseInt(line.getOptionValue("bufferSize", "100000")),
			Integer.parseInt(line.getOptionValue("maxOutstandingRecords", "10000")),
			line.hasOption("noKinesis")
			);

		populator.populate();
	}
}
