/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichr;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class EnrichrClientTest {
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static String json(String row) { return "{\"LIB\":[" + row + "]}"; }
    private static String row(String p, String odds, String score) {
        return "[1,\"term \\u03b1\"," + p + "," + odds + "," + score + ",[\"TP53\",\"TP53\"],0.2,0,0]";
    }
    private static EnrichrClient.Settings settings() {
        return new EnrichrClient.Settings(Duration.ofSeconds(3), 1048576, 1048576, 10000, Duration.ZERO);
    }
    private static EnrichrClient.Reply reply(String body) { return new EnrichrClient.Reply(200, Map.of(), bytes(body), true, null); }

    @Test void exactTokensAndRawOrderingSurviveWithoutDoubleUnderflow() throws Exception {
        var result = EnrichrResponse.results(bytes(json(row("1e-400", "1.50", "1380.0000000000000001"))), "LIB");
        var r = result.rows().get(0);
        assertEquals("1e-400", r.pValue().token()); assertTrue(r.pValue().decimal().signum() > 0);
        assertEquals("1.50", r.oddsRatio().token()); assertEquals("1380.0000000000000001", r.combinedScore().token());
        assertEquals(List.of("TP53", "TP53"), r.overlappingGenes()); assertEquals("term α", r.term());
        assertThrows(UnsupportedOperationException.class, () -> result.rows().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.overlappingGenes().clear());
    }
    @Test void sourceZeroAndPositiveInfiniteScoresAreRetainedAndWarned() throws Exception {
        var r = EnrichrResponse.results(bytes(json(row("0", "Infinity", "Infinity"))), "LIB");
        assertEquals("0", r.rows().get(0).pValue().token());
        assertEquals("Infinity", r.rows().get(0).oddsRatio().token());
        assertTrue(r.warnings().stream().anyMatch(s -> s.contains("P=0")));
        assertTrue(r.warnings().stream().anyMatch(s -> s.contains("infinite")));
        assertThrows(ArithmeticException.class, () -> r.rows().get(0).oddsRatio().decimal());
    }
    @Test void invalidProbabilitiesAndScoresNeverBecomeResults() {
        for (String p : List.of("NaN", "Infinity", "-Infinity", "-0.1", "1.01", "null", "\"0.1\""))
            assertThrows(IOException.class, () -> EnrichrResponse.results(bytes(json(row(p, "1", "2"))), "LIB"), p);
        for (String score : List.of("NaN", "-Infinity", "-2", "null"))
            assertThrows(IOException.class, () -> EnrichrResponse.results(bytes(json(row("0.1", score, "2"))), "LIB"), score);
    }
    @Test void malformedIdentitySchemaAndJsonAreRefused() {
        for (String text : List.of("{\"LIB\":[],\"LIB\":[]}", "{\"other\":[]}", "{\"error\":\"unsupported\"}",
                "{\"LIB\":[[1,\"x\",0.1]]}", "{\"LIB\":[]} trailing", "{\"LIB\":[[1,\"\\ud800\",0.1,1,1,[],0.2,0,0]]}",
                "{\"LIB\":[[1,\"x\",01,1,1,[],0.2,0,0]]}", json(row("1e-100000", "1", "2")),
                "{\"LIB\":[" + row("0.1","1","2") + "," + row("0.2","1","2") + "]}"))
            assertThrows(IOException.class, () -> EnrichrResponse.results(bytes(text), "LIB"), text);
        assertThrows(IOException.class, () -> EnrichrResponse.results(new byte[]{(byte)0xc3,0x28}, "LIB"));
    }
    @Test void emptyResultIsExplicitAndNotInventedAsNegativeBiology() throws Exception {
        var r = EnrichrResponse.results(bytes("{\"LIB\":[]}"), "LIB");
        assertTrue(r.rows().isEmpty()); assertTrue(r.warnings().stream().anyMatch(s -> s.contains("pathway absence")));
    }
    @Test void libraryImportPreservesSourceNamesDuplicatesAndEmptyFields() throws Exception {
        var lib = EnrichrResponse.library(bytes("term\t\tTP53\tTP53\t\nother\tdesc\t brca1 \n"), "LIB");
        assertEquals(List.of("TP53", "TP53", ""), lib.terms().get(0).genes());
        assertEquals(" brca1 ", lib.terms().get(1).genes().get(0));
        assertTrue(lib.warnings().stream().anyMatch(s -> s.contains("duplicate")));
        assertThrows(IOException.class, () -> EnrichrResponse.library(bytes("a\t\tX\na\t\tY\n"), "LIB"));
        assertThrows(IOException.class, () -> EnrichrResponse.library(new byte[0], "LIB"));
    }
    @Test void customUsesVerifiedPostAndNeverMixesChannels() throws Exception {
        var requests = new ArrayList<EnrichrClient.Request>();
        var session = new EnrichrClient(settings(), request -> {
            requests.add(request);
            String p = request.uri().getPath();
            return reply(p.endsWith("addList") ? "{\"userListId\":123,\"shortId\":\"abc\"}" :
                p.endsWith("addbackground") ? "{\"backgroundid\":\"bg\"}" : "{\"LIB\":[]}");
        }).openSession();
        var list = session.submit(EnrichrClient.Channel.CUSTOM_BACKGROUND, List.of("TP53", "BRCA1", "TP53"), "synthetic");
        var bg = session.background(List.of("TP53", "BRCA1", "RAD51"));
        session.enrich(list, bg, "LIB");
        assertEquals("POST", requests.get(2).method());
        assertEquals("/speedrichr/api/backgroundenrich", requests.get(2).uri().getPath());
        assertTrue(new String(requests.get(0).body(), StandardCharsets.UTF_8).contains("TP53\nBRCA1\nTP53"));
        assertThrows(IllegalArgumentException.class, () -> session.enrich(list, "LIB"));
        assertEquals(3, requests.size());
        var standard = new EnrichrClient.Submission(EnrichrClient.Channel.STANDARD, "123", "abc");
        assertThrows(IllegalArgumentException.class, () -> session.enrich(standard, bg, "LIB"));
    }
    @Test void standardSubmissionViewAndCatalogRemainSeparate() throws Exception {
        var requests = new ArrayList<EnrichrClient.Request>();
        var s = new EnrichrClient(settings(), r -> {
            requests.add(r);
            return reply(r.uri().getPath().endsWith("datasetStatistics") ? "{\"statistics\":[{\"libraryName\":\"A B\"}]}" :
                r.uri().getPath().endsWith("addList") ? "{\"userListId\":987,\"shortId\":\"shared\"}" :
                r.uri().getPath().endsWith("view") ? "{\"genes\":[\"TP53\",\"BRCA1\"]}" : "{\"A B\":[]}");
        }).openSession();
        assertEquals(List.of("A B"), s.libraries());
        var sub = s.submit(EnrichrClient.Channel.STANDARD, List.of("BRCA1", "TP53"), "");
        assertEquals(List.of("TP53", "BRCA1"), s.view(sub));
        s.enrich(sub, "A B"); assertTrue(requests.get(3).uri().toString().endsWith("backgroundType=A+B"));
        assertEquals("GET", requests.get(3).method());
    }
    @Test void serviceErrorsRetainRawResponseAndDoNotRetry() {
        int[] calls = {0};
        var s = new EnrichrClient(settings(), r -> {calls[0]++; return reply("{\"error\":\"api endpoint not supported\"}");}).openSession();
        var sub = new EnrichrClient.Submission(EnrichrClient.Channel.STANDARD, "123", "abc");
        var failure = assertThrows(EnrichrClient.Failure.class, () -> s.enrich(sub, "LIB"));
        assertEquals(1, calls[0]); assertEquals(1, failure.exchanges().size());
        assertTrue(new String(failure.exchanges().get(0).reply().body(), StandardCharsets.UTF_8).contains("not supported"));
        byte[] changed = failure.exchanges().get(0).reply().body(); changed[0] = 0;
        assertEquals('{', failure.exchanges().get(0).reply().body()[0]);
    }
    @Test void networkFailureAndNon200BodiesStayUnadmittedAndCaptured() {
        var sub = new EnrichrClient.Submission(EnrichrClient.Channel.STANDARD, "123", "abc");
        var unavailable = new EnrichrClient(settings(), r -> {throw new IOException("unavailable");}).openSession();
        var failed = assertThrows(EnrichrClient.Failure.class, () -> unavailable.enrich(sub, "LIB"));
        assertFalse(failed.exchanges().get(0).reply().complete());
        var service = new EnrichrClient(settings(), r -> new EnrichrClient.Reply(503, Map.of(), bytes("maintenance"), true, null)).openSession();
        var status = assertThrows(EnrichrClient.Failure.class, () -> service.enrich(sub, "LIB"));
        assertEquals(503, status.exchanges().get(0).reply().status());
        assertEquals("maintenance", new String(status.exchanges().get(0).reply().body(), StandardCharsets.UTF_8));
    }
    @Test void literalInputFailuresHappenBeforeAnyUpload() {
        var s = new EnrichrClient(settings(), r -> {fail("Input rejection must not transmit"); return null;}).openSession();
        for (List<String> genes : List.of(List.<String>of(), List.of(" TP53"), List.of("TP53\nBRCA1"), List.of("TP53,0.8"), List.of("\ud800")))
            assertThrows(IllegalArgumentException.class, () -> s.submit(EnrichrClient.Channel.STANDARD, genes, "test"));
        assertTrue(s.exchanges().isEmpty());
    }
    @Test void actualHttpTransportRetainsCapacityPrefix() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/large", x -> {byte[] data = bytes("0123456789abcdef"); x.sendResponseHeaders(200, data.length); x.getResponseBody().write(data); x.close();});
        server.start();
        try {
            var r = new EnrichrClient.HttpTransport(Duration.ofSeconds(2)).send(new EnrichrClient.Request("GET",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/large"), null, new byte[0], Duration.ofSeconds(2), 8));
            assertFalse(r.complete()); assertEquals(200, r.status()); assertArrayEquals(bytes("01234567"), r.body());
            assertEquals(EnrichrClient.TransferState.CAPACITY, r.transferState());
        } finally { server.stop(0); }
    }
    @Test void actualHttpTransportEnforcesWholeRequestTimeoutAndKeepsPartialBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {Thread t = new Thread(r); t.setDaemon(true); return t;}));
        server.createContext("/slow", x -> {
            x.sendResponseHeaders(200, 0); x.getResponseBody().write(bytes("part")); x.getResponseBody().flush();
            try {Thread.sleep(2000);} catch (InterruptedException e) {Thread.currentThread().interrupt();}
            x.close();
        });
        server.start();
        try {
            long start = System.nanoTime();
            var r = new EnrichrClient.HttpTransport(Duration.ofSeconds(1)).send(new EnrichrClient.Request("GET",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow"), null, new byte[0], Duration.ofMillis(500), 100));
            assertFalse(r.complete()); assertTrue(Duration.ofNanos(System.nanoTime()-start).toMillis() < 1800);
            assertEquals(200, r.status()); assertArrayEquals(bytes("part"), r.body());
            assertEquals(EnrichrClient.TransferState.UNAVAILABLE, r.transferState());
        } finally { server.stop(0); }
    }
    @Test void actualInterruptionIsPropagatedWithWireCapture() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        var s = new EnrichrClient(settings(), request -> {received.countDown(); Thread.sleep(5000); return reply("{}");}).openSession();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> {try {s.libraries();} catch (Throwable e) {error.set(e);}});
        worker.start(); assertTrue(received.await(2, TimeUnit.SECONDS)); worker.interrupt(); worker.join(2000);
        assertFalse(worker.isAlive()); assertInstanceOf(InterruptedException.class, error.get());
        assertFalse(s.exchanges().get(0).reply().complete());
        assertEquals(EnrichrClient.TransferState.INTERRUPTED, s.exchanges().get(0).reply().transferState());
    }
    @Test void availabilityIsSkippedButCapacityAndMalformedResultsFail() {
        var sub = new EnrichrClient.Submission(EnrichrClient.Channel.STANDARD, "123", "abc");
        for (int status : List.of(401, 403, 404, 408, 429, 500, 503)) {
            var s = new EnrichrClient(settings(), r -> new EnrichrClient.Reply(status, Map.of(), bytes("unavailable"), true, null)).openSession();
            assertEquals(3, assertThrows(EnrichrClient.Failure.class, () -> s.enrich(sub,"LIB")).suggestedExitCode());
        }
        for (var state : List.of(EnrichrClient.TransferState.CAPACITY, EnrichrClient.TransferState.INTERRUPTED)) {
            var s = new EnrichrClient(settings(), r -> new EnrichrClient.Reply(200, Map.of(), bytes("partial"), state, "retained")).openSession();
            assertEquals(2, assertThrows(EnrichrClient.Failure.class, () -> s.enrich(sub,"LIB")).suggestedExitCode());
        }
        var malformed = new EnrichrClient(settings(), r -> reply("{\"error\":\"invalid\"}")).openSession();
        assertEquals(2, assertThrows(EnrichrClient.Failure.class, () -> malformed.enrich(sub,"LIB")).suggestedExitCode());
        var bounded = new EnrichrClient(new EnrichrClient.Settings(Duration.ofSeconds(2), 1024, 128, 100, Duration.ZERO),
            r -> new EnrichrClient.Reply(503, Map.of(), bytes("unavailable"), true, null)).openSession();
        assertEquals(3, assertThrows(EnrichrClient.Failure.class, () -> bounded.enrich(sub,"LIB")).suggestedExitCode());
        assertEquals(2, assertThrows(EnrichrClient.Failure.class, () -> bounded.submit(EnrichrClient.Channel.STANDARD,
            List.of("TP53"), "")).suggestedExitCode());
        assertEquals(1, bounded.exchanges().size(), "Local request capacity must fail before another upload even after an earlier service skip");
    }
    @Test void originalLiveResponseFixturesCoverStandardBackgroundAndLibrary() throws Exception {
        Map<String,byte[]> files = new java.util.LinkedHashMap<>();
        try (var input = getClass().getResourceAsStream("/org/jlinalg/enrichr/api-reference-20261119.zip")) {
            assertNotNull(input);
            try (var z = new ZipInputStream(input)) {
                for (var entry = z.getNextEntry(); entry != null; entry = z.getNextEntry())
                    assertNull(files.put(entry.getName(), z.readAllBytes()));
            }
        }
        Map<?,?> expected = EnrichrResponse.object(EnrichrJson.read(files.get("expected-tokens.json")));
        for (String profile : List.of("standard-enrich", "background-enrich-post")) {
            var result = EnrichrResponse.results(files.get(profile + ".response"), "MSigDB_Hallmark_2020");
            List<?> original = EnrichrResponse.array(expected.get(profile));
            assertEquals(9, result.rows().size()); assertEquals(original.size(), result.rows().size());
            for (int i = 0; i < original.size(); i++) {
                var r = result.rows().get(i);
                assertEquals(EnrichrResponse.array(original.get(i)), List.of(Integer.toString(r.rank()), r.term(),
                    r.pValue().token(), r.oddsRatio().token(), r.combinedScore().token(), r.overlappingGenes(),
                    r.adjustedPValue().token(), r.oldPValue().token(), r.oldAdjustedPValue().token()));
            }
        }
        assertThrows(IOException.class, () -> EnrichrResponse.results(files.get("background-enrich-get.response"), "MSigDB_Hallmark_2020"));
    }
}
