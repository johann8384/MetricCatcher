package com.clearspring.thetan.metricCatcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.codehaus.jackson.map.util.LRUMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.CounterMetric;
import com.yammer.metrics.core.HistogramMetric;
import com.yammer.metrics.core.MeterMetric;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.reporting.AbstractReporter;

public class MetricCatcherTest {
    MetricCatcher metricCatcher;
    JSONMetric jsonMetric;
    String metricName;
    DatagramSocket sendingSocket;
    DatagramSocket listeningSocket;
    Map<String, Metric> metricCache;
    AbstractReporter reporter;
        
    @Before
    public void setUp() throws Exception {
        reporter = mock(AbstractReporter.class);
		metricCache = new LRUMap<String, Metric>(10, 10);
		listeningSocket = new DatagramSocket();
        metricCatcher = new MetricCatcher(listeningSocket, reporter, metricCache);
        
        jsonMetric = new JSONMetric();
        jsonMetric.setType("meter");
        // The Metrics class caches created metrics; we want fresh ones
        metricName = "foo.bar.baz.metric" + Math.random();
        jsonMetric.setName(metricName);
        jsonMetric.setTimestamp(((int)System.currentTimeMillis() / 1000));
        
		sendingSocket = new DatagramSocket();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testCreateMetric() {
        Metric metric = metricCatcher.createMetric(jsonMetric);

        assertEquals(MeterMetric.class, metric.getClass());
        
        MeterMetric meterMetric = ((MeterMetric)metric);
        // All metrics are in minutes :-( plz2fix
        assertEquals(TimeUnit.MINUTES, meterMetric.rateUnit());
    }
    
    @Test
    public void testCreateMetric_ShortName() {
        String name = "test" + Math.random();
        jsonMetric.setName(name);
        Metric metric = metricCatcher.createMetric(jsonMetric);
        Map<MetricName, Metric> allMetrics = Metrics.allMetrics();
        
        MetricName metricName = new MetricName(name, "", "");
        assertTrue(allMetrics.containsKey(metricName));
        
        Metric actual = allMetrics.get(metricName);
        assertEquals(metric, actual);
    }

    @Test
    public void testUpdateMetric() {
        MeterMetric metric = (MeterMetric)metricCatcher.createMetric(jsonMetric);
        
        metricCatcher.updateMetric(metric, 1);
        assertEquals(1, metric.count());
    }

    @Test
    public void testUpdateMetric_MultipleUpdates() {
        MeterMetric metric = (MeterMetric)metricCatcher.createMetric(jsonMetric);
        
        int count = 7;
        for (int x = 0; x < 7; x++)
	        metricCatcher.updateMetric(metric, 1);
        
        assertEquals(count, metric.count());
    }

    @Test
    public void testUpdateMetric_Meter_MarkWithZeroHasNoEffect() {
        MeterMetric metric = (MeterMetric)metricCatcher.createMetric(jsonMetric);
        metricCatcher.updateMetric(metric, 0);
        assertEquals(0, metric.count());
    }

    @Test
    public void testUpdateMetric_Counter_Increment() {
        jsonMetric.setType("counter");
        CounterMetric metric = (CounterMetric)metricCatcher.createMetric(jsonMetric);
        
        metricCatcher.updateMetric(metric, 7);
        assertEquals(7, metric.count());
    }
    
    @Test
    public void testUpdateMetric_Counter_IncrementMultipleTimes() {
        jsonMetric.setType("counter");
        CounterMetric metric = (CounterMetric)metricCatcher.createMetric(jsonMetric);
        
        int count = 7;
        for (int x = 0; x < 7; x++)
	        metricCatcher.updateMetric(metric, 1);
        
        assertEquals(count, metric.count());
    }

    @Test
    public void testUpdateMetric_Counter_Decrement() {
        jsonMetric.setType("counter");
        CounterMetric metric = (CounterMetric)metricCatcher.createMetric(jsonMetric);
        
        metricCatcher.updateMetric(metric, -7);
        assertEquals(-7, metric.count());
    }
    
    @Test
    public void testUpdateMetric_Counter_Clear() {
        jsonMetric.setType("counter");
        CounterMetric metric = (CounterMetric)metricCatcher.createMetric(jsonMetric);
        
        metricCatcher.updateMetric(metric, 1);
        assertEquals(1, metric.count());
        
        metricCatcher.updateMetric(metric, 0);
        assertEquals(0, metric.count());
    }
    
    @Test
    public void testUpdateMetric_Histogram() {
        jsonMetric.setType("histogram");
        jsonMetric.setBiased(false);
        HistogramMetric metric = (HistogramMetric)metricCatcher.createMetric(jsonMetric);
        
        metricCatcher.updateMetric(metric, 1);
        assertEquals(1, metric.count());
    }
    
    @Test
    public void testRun() throws IOException, InterruptedException {
		String json = "{\"unique\":\"b21407b0dbd00d9b87e68206c600d0bc1316647776\"," +
		              "\"metrics\":[" +
                         "{\"name\":\"" + metricName + "\"," +
	                      "\"value\":1," +
	                      "\"type\":\"counter\"," +
	                      "\"timestamp\":1316647781}" +
                      "]}";
		byte[] jsonBytes = json.getBytes();
		sendingSocket.send(new DatagramPacket(jsonBytes, jsonBytes.length, listeningSocket.getLocalAddress(), listeningSocket.getLocalPort()));
		
		metricCatcher.start();
		Thread.sleep(500);
		metricCatcher.shutdown();
		
		assertTrue(metricCache.containsKey(metricName));
    }
    
    @Test
    public void testRun_MultipleUpdatePackets() throws IOException, InterruptedException {
        String json;
        byte[] jsonBytes;
        
		json = "{\"unique\":\"foo\"," +
               "\"metrics\":[" +
                    "{\"name\":\"" + metricName +
                    "\",\"value\":1," +
                    "\"type\":\"counter\"," +
                    "\"timestamp\":1316647781}" +
                "]}";
		jsonBytes = json.getBytes();
		sendingSocket.send(new DatagramPacket(jsonBytes, jsonBytes.length, listeningSocket.getLocalAddress(), listeningSocket.getLocalPort()));
		
		json = "{\"unique\":\"bar\"," +
               "\"metrics\":[" +
                    "{\"name\":\"" + metricName +
                    "\",\"value\":1," +
                    "\"type\":\"counter\"," +
                    "\"timestamp\":1316647783}" +
               "]}";
		jsonBytes = json.getBytes();
		sendingSocket.send(new DatagramPacket(jsonBytes, jsonBytes.length, listeningSocket.getLocalAddress(), listeningSocket.getLocalPort()));
		
		metricCatcher.start();
		Thread.sleep(500);
		metricCatcher.shutdown();
		
		assertEquals(2, ((CounterMetric)metricCache.get(metricName)).count());
    }
    
    @Test
    public void testRun_MultipleIdenticalUpdatePacketsDiscarded() throws IOException, InterruptedException {
        String json;
        byte[] jsonBytes;
        
		json = "{\"unique\":\"foo\"," +
               "\"metrics\":[" +
                    "{\"name\":\"" + metricName +
                    "\",\"value\":1," +
                    "\"type\":\"counter\"," +
                    "\"timestamp\":1316647781}" +
                "]}";
		jsonBytes = json.getBytes();
		sendingSocket.send(new DatagramPacket(jsonBytes, jsonBytes.length, listeningSocket.getLocalAddress(), listeningSocket.getLocalPort()));
		
		json = "{\"unique\":\"foo\"," +
               "\"metrics\":[" +
                    "{\"name\":\"" + metricName +
                    "\",\"value\":1," +
                    "\"type\":\"counter\"," +
                    "\"timestamp\":1316647783}" +
               "]}";
		jsonBytes = json.getBytes();
		sendingSocket.send(new DatagramPacket(jsonBytes, jsonBytes.length, listeningSocket.getLocalAddress(), listeningSocket.getLocalPort()));
		
		metricCatcher.start();
		Thread.sleep(500);
		metricCatcher.shutdown();
		
		assertEquals(1, ((CounterMetric)metricCache.get(metricName)).count());
    }
    
    @Test
    public void testRun_MultipleUpdatesInOnePacket() throws IOException, InterruptedException {
        String secondMetricName = metricName + "2";
		String json = "{\"unique\":\"foo\"," +
		              "\"metrics\":[" +
		                   "{\"name\":\"" + metricName +
		                   "\",\"value\":1," +
		                   "\"type\":\"counter\"," +
		                   "\"timestamp\":1316647781}," +
		                   "{\"name\":\"" + secondMetricName +
		                   "\",\"value\":7," +
		                   "\"type\":\"meter\"," +
		                   "\"timestamp\":1316647781}" +
		              "]}";
		byte[] jsonBytes = json.getBytes();
		sendingSocket.send(new DatagramPacket(jsonBytes, jsonBytes.length, listeningSocket.getLocalAddress(), listeningSocket.getLocalPort()));
		
		metricCatcher.start();
		Thread.sleep(500);
		metricCatcher.shutdown();
		
		assertEquals(1, ((CounterMetric)metricCache.get(metricName)).count());
		assertEquals(7, ((MeterMetric)metricCache.get(secondMetricName)).count());
    }

//    @Test
//    public void testRun() throws IOException {
//        // Force feed a metric into the catcher
//        AbstractReporter reporter = mock(AbstractReporter.class);
//		Map<String, Metric> metricCache = new LRUMap<String, Metric>(10, 10);
//        Metric metric = metricCatcher.createMetric(jsonMetric);
//        metricCache.put(metricName, metric);
//        metricCatcher = new MetricCatcher(new DatagramSocket(), reporter, metricCache);
//    }
}