/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.gprofiler;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Original g:GOSt service calls with explicit settings and retained wire bytes.
 * Statistical calculation, identifier mapping and GO highlighting run upstream.
 * No R-client plotting, identifier correction or alternate database is inferred. */
public final class GprofilerGostClient implements AutoCloseable {
    private static final String VERSION = "e[1-9][0-9]{1,4}_eg[0-9]{1,5}_p[0-9]{1,5}_[a-f0-9]{8,64}";
    private static final Set<String> FLAGS = Set.of("all_results", "ordered", "combined", "measure_underrepresentation",
        "no_iea", "no_evidences", "highlight");
    private static final Set<String> FIELDS = Set.of("organism", "query", "sources", "user_threshold", "all_results",
        "ordered", "combined", "measure_underrepresentation", "no_iea", "domain_scope", "numeric_ns",
        "significance_threshold_method", "background", "output", "no_evidences", "highlight");

    /** Empty archive selects the current service. A null build permits metadata discovery only. */
    public record Service(String archive, String requiredBuild) {
        public Service {
            if (archive == null || (!archive.isEmpty() && !archive.matches("e[1-9][0-9]{1,4}_eg[0-9]{1,5}_p[0-9]{1,5}")))
                throw new IllegalArgumentException("Require an explicit archive3 key or empty current-service key");
            if (requiredBuild != null && (!requiredBuild.matches(VERSION)
                    || (!archive.isEmpty() && !requiredBuild.startsWith(archive + "_"))))
                throw new IllegalArgumentException("Require the complete expected build identifier matching the selected archive");
        }
        URI endpoint(String operation) {
            return URI.create("https://biit.cs.ut.ee/" + (archive.isEmpty() ? "gprofiler/" : "gprofiler_archive3/" + archive + "/")
                + "api/" + operation);
        }
    }
    public record Settings(Duration timeout, int maximumRequestBytes, int maximumResponseBytes,
                           int maximumIdentifierOccurrences, Duration requestInterval) {
        public Settings {
            if (timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0 || timeout.compareTo(Duration.ofMinutes(5)) > 0
                    || maximumRequestBytes < 1 || maximumRequestBytes > 67_108_864
                    || maximumResponseBytes < 1 || maximumResponseBytes > 67_108_864
                    || maximumIdentifierOccurrences < 1 || maximumIdentifierOccurrences > 1_000_000
                    || requestInterval == null || requestInterval.isNegative() || requestInterval.compareTo(Duration.ofMinutes(1)) > 0)
                throw new IllegalArgumentException("Invalid explicit API limits");
        }
        public static Settings defaults() {
            return new Settings(Duration.ofSeconds(45), 8_388_608, 8_388_608, 200_000, Duration.ofSeconds(1));
        }
    }
    public enum State { COMPLETE, UNAVAILABLE, CAPACITY, TIMEOUT, INTERRUPTED }
    public record Request(URI uri, byte[] body, Duration timeout, int maximumResponseBytes) {
        public Request {
            java.util.Objects.requireNonNull(uri); java.util.Objects.requireNonNull(timeout); body = body.clone();
            if (maximumResponseBytes < 1 || maximumResponseBytes > 67_108_864
                    || timeout.compareTo(Duration.ofMillis(1)) < 0 || timeout.compareTo(Duration.ofMinutes(5)) > 0)
                throw new IllegalArgumentException("Require bounded positive request timeout and response capacity");
        }
        @Override public byte[] body() { return body.clone(); }
    }
    public record Reply(int status, Map<String,List<String>> headers, byte[] body, State state, String failure) {
        public Reply {
            java.util.Objects.requireNonNull(state);
            var copy = new LinkedHashMap<String,List<String>>();
            headers.forEach((key,value) -> copy.put(key, List.copyOf(value)));
            headers = Map.copyOf(copy); body = body.clone();
        }
        @Override public byte[] body() { return body.clone(); }
    }
    public record Exchange(Request request, Reply reply) { }
    /** The unmodified response is authoritative; no floating-point conversion is required. */
    public record Result(String observedBuild, int reportedTerms, byte[] response) {
        public Result { response = response.clone(); }
        @Override public byte[] response() { return response.clone(); }
    }
    public interface Transport extends AutoCloseable {
        Reply send(Request request) throws IOException, InterruptedException;
        @Override default void close() throws IOException { }
    }
    public static final class Failure extends IOException {
        private static final long serialVersionUID = 1L;
        // Persist wire bytes explicitly; Java exception serialization is not a capture format.
        @SuppressWarnings("serial") private final List<Exchange> exchanges;
        private final int exitCode;
        Failure(String message, List<Exchange> exchanges, int exitCode) {
            super(message); this.exchanges = List.copyOf(exchanges); this.exitCode = exitCode;
        }
        public List<Exchange> exchanges() { return exchanges; }
        /** SKIP3 requires the named exact permitted service/build; FAIL2 admits no result. */
        public int suggestedExitCode() { return exitCode; }
    }
    private final Settings settings;
    private final Transport transport;
    private boolean closed;
    public GprofilerGostClient(Settings settings) { this(settings, new GprofilerHttpTransport(settings.timeout())); }
    public GprofilerGostClient(Settings settings, Transport transport) {
        this.settings = java.util.Objects.requireNonNull(settings);
        this.transport = java.util.Objects.requireNonNull(transport);
    }
    /** Sessions are sequential; a client and its sessions must not be shared across threads. */
    public Session openSession() { if (closed) throw new IllegalStateException("Client is closed"); return new Session(); }
    @Override public void close() throws IOException { if (!closed) { closed = true; transport.close(); } }

    public final class Session {
        private final List<Exchange> exchanges = new ArrayList<>();
        private long previousRequest;
        private Session() { }
        public List<Exchange> exchanges() { return List.copyOf(exchanges); }
        /** Metadata-only discovery submits no gene identifiers. */
        public Result versions(Service service, String organism) throws IOException {
            literal(organism, "organism");
            Map<String,Object> root = send(service.endpoint("util/data_versions"), Map.of("organism", organism));
            String observed;
            try { observed = text(root.get("gprofiler_version"), "metadata build"); }
            catch (IOException e) { throw problem(e.getMessage(), 2); }
            checkBuild(service, observed);
            return new Result(observed, 0, last().body());
        }
        /** All supported scientific settings are mandatory; duplicate/order-preserving input is never repaired. */
        public Result profile(Service service, Map<String,Object> parameters, boolean allowUpload) throws IOException {
            if (!allowUpload) throw new IOException("Gene identifiers and query names are submitted externally; explicitly allow upload");
            if (service.requiredBuild() == null) throw new IOException("Discover metadata first, then require a complete build identifier");
            validate(parameters);
            Map<String,Object> root = send(service.endpoint("gost/profile/"), parameters);
            Object rawMeta = root.get("meta"), rawRows = root.get("result");
            if (!(rawMeta instanceof Map<?,?> meta) || !(rawRows instanceof List<?> rows))
                throw problem("Response lacks complete result array and metadata", 2);
            String observed;
            try { observed = text(meta.get("version"), "result build"); }
            catch (IOException e) { throw problem(e.getMessage(), 2); }
            checkBuild(service, observed);
            for (Object row : rows) if (!(row instanceof Map<?,?>)) throw problem("Result row is not an object", 2);
            return new Result(observed, rows.size(), last().body());
        }
        private Reply last() { return exchanges.get(exchanges.size() - 1).reply(); }
        private void checkBuild(Service service, String observed) throws Failure {
            if (!observed.matches(VERSION)) throw problem("Malformed service build identifier", 2);
            if (service.requiredBuild() != null && !service.requiredBuild().equals(observed))
                throw problem("Exact required g:Profiler build unavailable: required " + service.requiredBuild()
                    + ", observed " + observed + ". Dependent analysis skipped; restore the exact archive or retain SKIP3; no build substituted", 3);
        }
        private Failure problem(String detail, int code) { return new Failure(detail, exchanges, code); }
        private Map<String,Object> send(URI uri, Map<String,Object> parameters) throws IOException {
            if (closed) throw new IOException("Client is closed");
            byte[] body = GprofilerJson.write(parameters, settings.maximumRequestBytes());
            Request request = new Request(uri, body, settings.timeout(), settings.maximumResponseBytes());
            Reply reply;
            try {
                long remaining = settings.requestInterval().toNanos() - (System.nanoTime() - previousRequest);
                if (previousRequest != 0 && remaining > 0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(remaining);
                previousRequest = System.nanoTime(); reply = transport.send(request);
            } catch (GprofilerHttpTransport.InterruptedTransfer e) {
                exchanges.add(new Exchange(request, e.reply)); Thread.currentThread().interrupt();
                throw problem("Interrupted HTTP transfer; partial bytes retained", 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); reply = new Reply(0, Map.of(), new byte[0], State.INTERRUPTED, e.toString());
            } catch (IOException e) { reply = new Reply(0, Map.of(), new byte[0], State.UNAVAILABLE, e.toString()); }
            exchanges.add(new Exchange(request, reply));
            if (reply.body().length > settings.maximumResponseBytes() || reply.state() == State.CAPACITY)
                throw problem("Response capacity exceeded; no result; captured bytes retained", 2);
            if (reply.state() != State.COMPLETE)
                throw problem("HTTP " + reply.state() + ": " + reply.failure(), reply.state() == State.UNAVAILABLE ? 3 : 2);
            if (reply.status() != 200) throw problem("g:Profiler returned HTTP " + reply.status() + "; raw reply retained; no retry or redirect followed",
                Set.of(301,302,303,307,308,401,403,404,408,429).contains(reply.status()) || reply.status() >= 500 ? 3 : 2);
            try {
                Object parsed = GprofilerJson.read(reply.body());
                if (!(parsed instanceof Map<?,?> object)) throw new IOException("Require a JSON response object");
                var root = new LinkedHashMap<String,Object>();
                for (var entry : object.entrySet()) root.put((String)entry.getKey(), entry.getValue());
                if (root.containsKey("error") || root.containsKey("message")) throw new IOException("Service reported an error/message; inspect raw response");
                return root;
            } catch (IOException e) { throw problem("Invalid service response: " + e.getMessage(), 2); }
        }
    }
    private void validate(Map<String,Object> p) throws IOException {
        if (!p.keySet().equals(FIELDS)) throw new IOException("Declare exactly the 16 supported g:GOSt fields; no default or unknown setting is inferred");
        for (String flag : FLAGS) if (!(p.get(flag) instanceof Boolean)) throw new IOException("Require Boolean " + flag);
        literal(text(p.get("organism"), "organism"), "organism");
        if (!(p.get("numeric_ns") instanceof String)) throw new IOException("Declare numeric_ns, including an explicit empty string if intended");
        if (!"json".equals(p.get("output"))) throw new IOException("Require output=json");
        if (!Set.of("annotated","known","custom","custom_annotated").contains(text(p.get("domain_scope"),"domain_scope"))) throw new IOException("Unsupported domain_scope");
        if (!Set.of("g_SCS","bonferroni","fdr").contains(text(p.get("significance_threshold_method"),"correction method"))) throw new IOException("Declare literal API correction method");
        byte[] threshold = GprofilerJson.write(p.get("user_threshold"), 256);
        Object parsed = GprofilerJson.read(threshold);
        if (!(parsed instanceof GprofilerJson.Numeric n)) throw new IOException("Require numeric user_threshold");
        try {
            var t = new java.math.BigDecimal(n.token());
            if (t.signum() < 0 || t.compareTo(java.math.BigDecimal.ONE) > 0) throw new IOException("user_threshold must lie in [0,1]");
        } catch (NumberFormatException e) { throw new IOException("Unrepresentable user_threshold", e); }
        if (!(p.get("sources") instanceof List<?> sources)) throw new IOException("Require explicit sources array");
        for (Object source : sources) literal(text(source,"source"),"source");
        int count = 0; Object query = p.get("query");
        if (query instanceof Map<?,?> queries) {
            if (queries.isEmpty() || queries.size() > 256) throw new IOException("Require 1..256 named queries");
            for (var entry : queries.entrySet()) {
                literal(text(entry.getKey(),"query name"),"query name"); count = identifiers(entry.getValue(), count, false);
            }
        } else count = identifiers(query, count, false);
        Object background = p.get("background");
        if (background != null) count = identifiers(background, count, true);
        if (p.get("domain_scope").toString().startsWith("custom") && (background == null || ((List<?>)background).isEmpty()))
            throw new IOException("Custom domain requires an explicit nonempty background array");
        if (count > settings.maximumIdentifierOccurrences()) throw new IOException("Combined query/background identifier capacity exceeded");
    }
    private int identifiers(Object value, int count, boolean allowEmpty) throws IOException {
        if (!(value instanceof List<?> list) || (!allowEmpty && list.isEmpty())) throw new IOException("Require explicit identifier arrays; no splitting or sorting");
        for (Object id : list) {
            literal(text(id,"identifier"),"identifier");
            if (++count > settings.maximumIdentifierOccurrences()) throw new IOException("Identifier capacity exceeded");
        }
        return count;
    }
    private static String text(Object value, String label) throws IOException {
        if (!(value instanceof String s)) throw new IOException("Require string " + label); return s;
    }
    private static void literal(String value, String label) throws IOException {
        if (value.isEmpty() || value.length() > 4096) throw new IOException("Require nonempty literal " + label + ", at most 4096 characters");
    }
}
