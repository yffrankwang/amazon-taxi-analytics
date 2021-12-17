package com.amazonaws.samples.taxi.kaja.consumer.events.kinesis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.junit.Test;

import com.amazonaws.samples.taxi.kaja.consumer.events.EventDeserializationSchema;
import com.amazonaws.samples.taxi.kaja.consumer.events.kinesis.Event;
import com.amazonaws.samples.taxi.kaja.consumer.events.kinesis.TripEvent;
import com.amazonaws.samples.taxi.kaja.consumer.events.kinesis.TripEventValidator;
import com.amazonaws.samples.taxi.kaja.consumer.utils.GeoUtils;

public class EventDeserializationSchemaTest {

	private void deserialize(String fin) throws Exception {
		if (!(new File(fin).canRead())) {
			return;
		}
		
		System.out.println("-------------------------------------------------------------------");
		System.out.println(fin);
		System.out.println("-------------------------------------------------------------------");

		EventDeserializationSchema edss = new EventDeserializationSchema();

		BufferedReader br = new BufferedReader(new FileReader(fin));

		int invalid = 0;
		int lineno = 0;
		String line;
		while ((line = br.readLine()) != null) {
			lineno++;
			Event evt = edss.deserialize(line.getBytes());
			if (evt == null || !(evt instanceof TripEvent)) {
				System.out.println(lineno + " - error event: " + line);
				return;
			}

			TripEvent te = (TripEvent)evt;
			if (!TripEventValidator.hasValidDatetime(te)) {
				if (invalid % 10000 == 0) {
					System.out.println(lineno + " - error time: " + line);
				}
				invalid++;
				continue;
			}

			if (!GeoUtils.hasValidCoordinates(te)) {
				if (invalid % 10000 == 0) {
					System.out.println(lineno + " - error geo: " + line);
				}
				invalid++;
				continue;
			}
		}
		
		br.close();
		System.out.println("invalid record " + invalid + " / " + lineno);
	}
	
	@Test
	public void testDeserializeGreen() throws Exception {
		deserialize("D:\\Develop\\Projects\\aws\\tlc\\data\\green_tripdata_2018-01.csv.json");
		//deserialize("D:\\Develop\\Projects\\aws\\tlc\\data\\green_tripdata_2014-01.csv.json");
	}
	
	@Test
	public void testDeserializeYellow() throws Exception {
		//deserialize("D:\\Develop\\Projects\\aws\\tlc\\data\\yellow_tripdata_2018-01.csv.json");
	}
}
