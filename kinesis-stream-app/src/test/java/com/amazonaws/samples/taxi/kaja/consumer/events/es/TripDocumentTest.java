package com.amazonaws.samples.taxi.kaja.consumer.events.es;

import org.junit.Test;

import com.amazonaws.samples.taxi.kaja.consumer.events.es.TripDocument;

public class TripDocumentTest {

	@Test
	public void testToString() throws Exception {
		TripDocument td = new TripDocument();
		System.out.println(td);
	}
}
