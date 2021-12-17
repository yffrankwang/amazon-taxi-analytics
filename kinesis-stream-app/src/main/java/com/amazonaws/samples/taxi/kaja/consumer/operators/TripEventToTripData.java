package com.amazonaws.samples.taxi.kaja.consumer.operators;

import java.time.Duration;

import org.apache.flink.api.common.functions.MapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.samples.taxi.kaja.consumer.events.flink.TripData;
import com.amazonaws.samples.taxi.kaja.consumer.events.kinesis.TripEvent;
import com.amazonaws.samples.taxi.kaja.consumer.utils.GeoUtils;

import ch.hsr.geohash.GeoHash;

public class TripEventToTripData implements MapFunction<TripEvent, TripData> {
	private static final long serialVersionUID = 1L;
	
	//private static final DecimalFormat decfmt = new DecimalFormat("#.000000");

	private static final Logger LOG = LoggerFactory.getLogger(TripEventToTripData.class);

	@Override
	public TripData map(TripEvent te) {
		try {
			LOG.debug("map TripEvent {}", te);

			GeoHash hash = GeoHash.withCharacterPrecision(te.pickupLatitude, te.pickupLongitude, 6);

			String geoHash = hash.toBase32();
			String location = te.pickupLatitude + "," + te.pickupLongitude;

			long tripDuration = Duration.ofMillis(Math.abs(te.dropoffDatetime.getTime() - te.pickupDatetime.getTime())).toMillis() / 1000;
			long tripDistance = distance(te.pickupLatitude, te.pickupLongitude, te.dropoffLatitude, te.dropoffLongitude);

			String hotspot = "";
			if (GeoUtils.nearJFK(te.dropoffLatitude, te.dropoffLongitude)) {
				hotspot = "JFK";
			} else if (GeoUtils.nearLGA(te.dropoffLatitude, te.dropoffLongitude)) {
				hotspot = "LGA";
			}

			return new TripData(location, geoHash, hotspot, tripDuration, tripDistance);
		} catch (Throwable e) {
			LOG.error("map TripEvent failed", e);
			return null;
		}
	}

	/**
	 * Calculate distance between two points in latitude and longitude taking into account height difference. If you are not interested in height difference pass 0.0. Uses Haversine method as its
	 * base. lat1, lon1 Start point lat2, lon2 End point el1 Start altitude in meters el2 End altitude in meters
	 * 
	 * @returns Distance in Meters
	 */
	public static long distance(double lat1, double lon1, double lat2, double lon2) {
		final int R = 6371; // Radius of the earth

		double latDistance = Math.toRadians(lat2 - lat1);
		double lonDistance = Math.toRadians(lon2 - lon1);
		double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
			+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double distance = R * c * 1000; // convert to meters

		distance = Math.pow(distance, 2);

		return (long)Math.sqrt(distance);
	}
}
