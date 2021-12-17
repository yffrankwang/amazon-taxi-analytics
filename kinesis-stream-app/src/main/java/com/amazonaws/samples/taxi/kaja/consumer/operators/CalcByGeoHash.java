package com.amazonaws.samples.taxi.kaja.consumer.operators;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Iterator;

import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.samples.taxi.kaja.consumer.events.es.TripDocument;
import com.amazonaws.samples.taxi.kaja.consumer.events.flink.TripData;
import com.google.common.collect.Iterables;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;

public class CalcByGeoHash implements WindowFunction<TripData, TripDocument, String, TimeWindow> {
	private static final long serialVersionUID = 1;

	private static final DecimalFormat GEOFMT = new DecimalFormat("#.0000000000");
	private static final Logger LOG = LoggerFactory.getLogger(CalcByGeoHash.class);

	@Override
	public void apply(String key, TimeWindow timeWindow, Iterable<TripData> iterable, Collector<TripDocument> collector) throws Exception {
		try {
			long count = Iterables.size(iterable);
			if (count < 1) {
				return;
			}

			TripData data = Iterables.get(iterable, 0);
			TripDocument doc = new TripDocument();

			doc.timestamp = Instant.ofEpochMilli(timeWindow.getEnd());
			doc.geohash = data.geohash;
			WGS84Point gp = GeoHash.fromGeohashString(doc.geohash).getPoint();
			doc.location = GEOFMT.format(gp.getLatitude()) + "," + GEOFMT.format(gp.getLongitude());
			doc.hotspot = data.hotspot;

			double sumTripSpeed = 0;

			Iterator<TripData> it = iterable.iterator();
			while (it.hasNext()) {
				data = it.next();
				doc.sumTripDistance += data.tripDistance;
				doc.sumTripDuration = data.tripDuration;
				if (data.tripDuration > 0) {
					sumTripSpeed += (double)(data.tripDistance * 60 * 60) / data.tripDuration / 1000;
				}
			}

			// seconds => minutes
//			doc.sumTripDuration /= 60;
			doc.avgTripDuration = doc.sumTripDuration / count;
			doc.avgTripDistance = doc.sumTripDistance / count;
			doc.avgTripSpeed = sumTripSpeed / count;
			doc.pickupCount = count;

			collector.collect(doc);
			
			LOG.debug("CalcByGeoHash collect {}: {}", count, doc.toString());
		} catch (Exception e) {
			LOG.error("CalcByGeoHash failed", e);
		}
	}
}
