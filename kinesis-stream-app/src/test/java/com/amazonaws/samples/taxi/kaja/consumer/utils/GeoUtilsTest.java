package com.amazonaws.samples.taxi.kaja.consumer.utils;

import org.apache.flink.streaming.api.windowing.time.Time;
import org.junit.Test;

import com.amazonaws.samples.taxi.kaja.consumer.events.kinesis.TripEvent;

public class GeoUtilsTest {

	@Test
	public void testParse() throws Exception {
		System.out.println(Time.minutes(5).toMilliseconds());
		System.out.println(TripEvent.FMT.parse("2010-01-01 00:00:00").getTime());
		System.out.println(Long.MIN_VALUE);
	}
}
