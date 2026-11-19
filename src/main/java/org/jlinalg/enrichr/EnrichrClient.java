/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit current human/mouse Enrichr API calls with bounded, retained wire captures. */
public final class EnrichrClient {
    public enum Channel { STANDARD, CUSTOM_BACKGROUND }
    public enum TransferState { COMPLETE, UNAVAILABLE, CAPACITY, INTERRUPTED }
    public record Submission(Channel channel, String userListId, String shortId) {
        public Submission {
            if (channel == null || userListId == null || !userListId.matches("[1-9][0-9]{0,31}")
                    || shortId == null || shortId.isEmpty()) throw new IllegalArgumentException("Invalid submission identity");
        }
    }
    public record Background(String id) {
        public Background { if (id == null || id.isEmpty()) throw new IllegalArgumentException("Background identity required"); }
    }
    public record Settings(Duration timeoutPerRequest, int maximumResponseBytes,
                           int maximumRequestBytes, int maximumGenes, Duration requestInterval) {
        public Settings {
            if (timeoutPerRequest == null || timeoutPerRequest.compareTo(Duration.ofMillis(1)) < 0
                    || timeoutPerRequest.compareTo(Duration.ofMinutes(5)) > 0
                    || maximumResponseBytes < 1 || maximumResponseBytes > 67_108_864
                    || maximumRequestBytes < 1 || maximumRequestBytes > 67_108_864
                    || maximumGenes < 1 || maximumGenes > 1_000_000
                    || requestInterval == null || requestInterval.isNegative()
                    || requestInterval.compareTo(Duration.ofSeconds(60)) > 0)
                throw new IllegalArgumentException("Invalid explicit API resource bounds");
        }
        public static Settings defaults() {
            return new Settings(Duration.ofSeconds(45), 8_388_608, 8_388_608, 200_000, Duration.ofSeconds(1));
        }
    }
    public record Request(String method, URI uri, String contentType, byte[] body,
                          Duration timeout, int maximumResponseBytes) {
        public Request { body = body.clone(); }
        @Override public byte[] body() { return body.clone(); }
    }
    public record Reply(int status, Map<String,List<String>> headers, byte[] body,
                        TransferState transferState, String failure) {
        public Reply {
            java.util.Objects.requireNonNull(transferState);
            Map<String,List<String>> copy = new LinkedHashMap<>();
            headers.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            headers = Map.copyOf(copy); body = body.clone();
        }
        public Reply(int status, Map<String,List<String>> headers, byte[] body, boolean complete, String failure) {
            this(status, headers, body, complete ? TransferState.COMPLETE : TransferState.UNAVAILABLE, failure);
        }
        public boolean complete() { return transferState == TransferState.COMPLETE; }
        @Override public byte[] body() { return body.clone(); }
    }
    public record Exchange(Request request, Reply reply) { }
    @FunctionalInterface public interface Transport {
        Reply send(Request request) throws IOException, InterruptedException;
    }
    /** Failures retain the completed/partial exchanges; callers must persist these diagnostics. */
    public static final class Failure extends IOException {
        private final List<Exchange> exchanges;
        private final Integer explicitExitCode;
        Failure(String message, List<Exchange> exchanges) { this(message, exchanges, null); }
        Failure(String message, List<Exchange> exchanges, Integer explicitExitCode) {
            super(message); this.exchanges = List.copyOf(exchanges); this.explicitExitCode = explicitExitCode;
        }
        public List<Exchange> exchanges() { return exchanges; }
        /** Missing/unavailable service prerequisites are SKIP3; capacity/data/protocol failures are FAIL2. */
        public int suggestedExitCode() {
            if (explicitExitCode != null) return explicitExitCode;
            if (exchanges.isEmpty()) return 2;
            Reply last = exchanges.get(exchanges.size() - 1).reply();
            if (last.transferState() == TransferState.CAPACITY || last.transferState() == TransferState.INTERRUPTED) return 2;
            if (last.transferState() == TransferState.UNAVAILABLE || List.of(0, 401, 403, 404, 408, 429).contains(last.status())
                    || last.status() >= 500) return 3;
            return 2;
        }
    }
    private static final URI MAIN = URI.create("https://maayanlab.cloud/Enrichr/");
    private static final URI SPEED = URI.create("https://maayanlab.cloud/speedrichr/api/");
    private final Settings settings;
    private final Transport transport;

    public EnrichrClient() { this(Settings.defaults()); }
    public EnrichrClient(Settings settings) {
        this(settings, new HttpTransport(settings.timeoutPerRequest()));
    }
    /** Injected transports are useful for deterministic source-response and failure controls. */
    public EnrichrClient(Settings settings, Transport transport) {
        this.settings = java.util.Objects.requireNonNull(settings);
        this.transport = java.util.Objects.requireNonNull(transport);
    }
    /** A session is sequential and must not be shared between threads. Creation performs no upload. */
    public Session openSession() { return new Session(); }

    public final class Session {
        private final List<Exchange> exchanges = new ArrayList<>();
        private long previousRequest;
        private Session() { }
        public List<Exchange> exchanges() { return List.copyOf(exchanges); }

        public List<String> libraries() throws IOException, InterruptedException {
            try {
                Map<?,?> root = EnrichrResponse.object(EnrichrJson.read(send("GET", MAIN.resolve("datasetStatistics"), null, new byte[0])));
                List<String> names = new ArrayList<>(); HashSet<String> seen = new HashSet<>();
                for (Object row : EnrichrResponse.array(root.get("statistics"))) {
                    String name = EnrichrResponse.text(EnrichrResponse.object(row).get("libraryName"));
                    EnrichrResponse.require(seen.add(name), "Duplicate library name"); names.add(name);
                }
                EnrichrResponse.require(!names.isEmpty(), "Empty library catalog");
                return List.copyOf(names);
            } catch (IOException e) { throw problem(e); }
        }
        public EnrichrResponse.Library library(String name) throws IOException, InterruptedException {
            label(name);
            try { return EnrichrResponse.library(send("GET", MAIN.resolve("geneSetLibrary?mode=text&libraryName=" + encode(name)), null, new byte[0]), name); }
            catch (IOException e) { throw problem(e); }
        }
        public Submission submit(Channel channel, List<String> genes, String description) throws IOException, InterruptedException {
            if (channel == null) throw new IllegalArgumentException("Choose standard or custom-background channel explicitly");
            String geneText = genes(genes);
            literal(description, true);
            String boundary = "OmicsMaticEnrichr" + UUID.randomUUID().toString().replace("-", "");
            String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"list\"\r\n\r\n" + geneText
                + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"description\"\r\n\r\n" + description
                + "\r\n--" + boundary + "--\r\n";
            try {
                Map<?,?> result = EnrichrResponse.object(EnrichrJson.read(send("POST", base(channel).resolve("addList"),
                    "multipart/form-data; boundary=" + boundary, body.getBytes(StandardCharsets.UTF_8))));
                Object rawId = result.get("userListId");
                EnrichrResponse.require(rawId instanceof EnrichrJson.Numeric, "Missing numeric userListId");
                String id;
                try { id = new EnrichrResponse.Numeric(((EnrichrJson.Numeric)rawId).token()).decimal().toBigIntegerExact().toString(); }
                catch (ArithmeticException | IllegalArgumentException e) { throw new IOException("Invalid service userListId", e); }
                return new Submission(channel, id, EnrichrResponse.text(result.get("shortId")));
            } catch (IOException | IllegalArgumentException e) { throw problem(e); }
        }
        public Background background(List<String> genes) throws IOException, InterruptedException {
            String geneText = genes(genes);
            try {
                Map<?,?> result = EnrichrResponse.object(EnrichrJson.read(send("POST", SPEED.resolve("addbackground"),
                    "application/x-www-form-urlencoded", form(Map.of("background", geneText)))));
                return new Background(EnrichrResponse.text(result.get("backgroundid")));
            } catch (IOException | IllegalArgumentException e) { throw problem(e); }
        }
        public EnrichrResponse.Results enrich(Submission list, String library) throws IOException, InterruptedException {
            if (list.channel() != Channel.STANDARD) throw new IllegalArgumentException("A custom-background submission requires its explicit background");
            label(library);
            try { return EnrichrResponse.results(send("GET", MAIN.resolve("enrich?userListId=" + list.userListId()
                + "&backgroundType=" + encode(library)), null, new byte[0]), library); }
            catch (IOException e) { throw problem(e); }
        }
        public EnrichrResponse.Results enrich(Submission list, Background background, String library) throws IOException, InterruptedException {
            if (list.channel() != Channel.CUSTOM_BACKGROUND) throw new IllegalArgumentException("Standard Enrichr IDs cannot be used with Speedrichr backgrounds");
            label(library);
            // POST is verified live and used by the pinned enrichR/backr sources.
            // The official smartAPI GET discrepancy is retained in the source audit.
            try { return EnrichrResponse.results(send("POST", SPEED.resolve("backgroundenrich"),
                "application/x-www-form-urlencoded", form(Map.of("userListId", list.userListId(),
                    "backgroundid", background.id(), "backgroundType", library))), library); }
            catch (IOException e) { throw problem(e); }
        }
        /** Server-prepared identities are exposed separately, never substituted for submitted IDs. */
        public List<String> view(Submission list) throws IOException, InterruptedException {
            if (list.channel() != Channel.STANDARD) throw new IllegalArgumentException("The qualified view endpoint is standard Enrichr only");
            try {
                Map<?,?> result = EnrichrResponse.object(EnrichrJson.read(send("GET", MAIN.resolve("view?userListId=" + list.userListId()), null, new byte[0])));
                List<String> genes = new ArrayList<>();
                for (Object value : EnrichrResponse.array(result.get("genes"))) genes.add(EnrichrResponse.text(value));
                return List.copyOf(genes);
            } catch (IOException e) { throw problem(e); }
        }
        private byte[] send(String method, URI uri, String contentType, byte[] body) throws IOException, InterruptedException {
            if (body.length > settings.maximumRequestBytes()) throw new Failure("Request byte capacity exceeded before transmission", exchanges, 2);
            long wait = settings.requestInterval().toNanos() - (System.nanoTime() - previousRequest);
            if (previousRequest != 0 && wait > 0) TimeUnit.NANOSECONDS.sleep(wait);
            Request request = new Request(method, uri, contentType, body, settings.timeoutPerRequest(), settings.maximumResponseBytes());
            Reply reply;
            try { reply = transport.send(request); }
            catch (IOException e) { reply = new Reply(0, Map.of(), new byte[0], false, e.toString()); }
            catch (InterruptedException e) {
                Reply partial = e instanceof InterruptedTransfer transfer ? transfer.reply
                    : new Reply(0, Map.of(), new byte[0], TransferState.INTERRUPTED, "Interrupted; no complete response");
                exchanges.add(new Exchange(request, partial));
                Thread.currentThread().interrupt(); throw e;
            } finally { previousRequest = System.nanoTime(); }
            exchanges.add(new Exchange(request, reply));
            if (!reply.complete() || reply.status() != 200 || reply.body().length > settings.maximumResponseBytes())
                throw problem(new IOException("No complete Enrichr response: HTTP " + reply.status() + "; " + reply.failure()));
            return reply.body();
        }
        private Failure problem(Exception error) {
            if (error instanceof Failure failure) return failure;
            return new Failure(error.getMessage(), exchanges);
        }
    }

    private String genes(List<String> genes) {
        if (genes == null || genes.isEmpty() || genes.size() > settings.maximumGenes()) throw new IllegalArgumentException("Explicit nonempty gene list exceeds capacity or is missing");
        long length = 0;
        for (String gene : genes) {
            literal(gene, false);
            if (!gene.equals(gene.strip()) || gene.indexOf(',') >= 0 || gene.length() > 1024)
                throw new IllegalArgumentException("Require prepared literal unweighted symbols; no trimming, fuzzy-score interpretation or ID repair");
            length += gene.length() + 1L;
        }
        if (length > settings.maximumRequestBytes()) throw new IllegalArgumentException("Gene-list request capacity exceeded");
        return String.join("\n", genes);
    }
    private static URI base(Channel channel) { return channel == Channel.STANDARD ? MAIN : SPEED; }
    private static void label(String value) { literal(value, false); if (value.length() > 4096) throw new IllegalArgumentException("Library label capacity exceeded"); }
    private static void literal(String value, boolean emptyAllowed) {
        if (value == null || (!emptyAllowed && value.isEmpty()) || value.length() > 65536)
            throw new IllegalArgumentException("Literal identity/description required within capacity");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) throw new IllegalArgumentException("Control character in literal input");
            if (Character.isHighSurrogate(c)) {
                if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) throw new IllegalArgumentException("Unpaired surrogate in literal input");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Unpaired surrogate in literal input");
        }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static byte[] form(Map<String,String> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"))
            .getBytes(StandardCharsets.UTF_8);
    }

    private static final class InterruptedTransfer extends InterruptedException {
        final Reply reply;
        InterruptedTransfer(Reply reply) { super("HTTP transfer interrupted; captured prefix retained"); this.reply = reply; }
    }
    static final class HttpTransport implements Transport {
        private final HttpClient http;
        HttpTransport(Duration timeout) {
            http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1).build();
        }
        @Override public Reply send(Request request) throws InterruptedException {
            AtomicReference<HttpResponse.ResponseInfo> info = new AtomicReference<>();
            AtomicReference<LimitedBody> body = new AtomicReference<>();
            HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri()).timeout(request.timeout())
                .header("User-Agent", "OmicsMatic-Enrichr/1").header("Accept", "application/json, text/plain, */*");
            if (request.contentType() != null) builder.header("Content-Type", request.contentType());
            builder.method(request.method(), request.body().length == 0 ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(request.body()));
            CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(builder.build(), r -> {
                info.set(r); LimitedBody subscriber = new LimitedBody(request.maximumResponseBytes()); body.set(subscriber); return subscriber;
            });
            try {
                HttpResponse<byte[]> response = future.get(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
                return new Reply(response.statusCode(), response.headers().map(), response.body(), true, null);
            } catch (InterruptedException e) {
                future.cancel(true);
                LimitedBody partial = body.get();
                if (partial != null) partial.cancel();
                HttpResponse.ResponseInfo headers = info.get();
                throw new InterruptedTransfer(new Reply(headers == null ? 0 : headers.statusCode(),
                    headers == null ? Map.of() : headers.headers().map(),
                    partial == null ? new byte[0] : partial.snapshot(), TransferState.INTERRUPTED, "Interrupted; no complete response"));
            } catch (Exception e) {
                future.cancel(true);
                HttpResponse.ResponseInfo headers = info.get(); LimitedBody partial = body.get();
                if (partial != null) partial.cancel();
                return new Reply(headers == null ? 0 : headers.statusCode(), headers == null ? Map.of() : headers.headers().map(),
                    partial == null ? new byte[0] : partial.snapshot(),
                    partial != null && partial.exceeded() ? TransferState.CAPACITY : TransferState.UNAVAILABLE, e.toString());
            }
        }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximum;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> completed = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private boolean exceeded;
        LimitedBody(int maximum) { this.maximum = maximum; }
        @Override public CompletionStage<byte[]> getBody() { return completed; }
        @Override public synchronized void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public synchronized void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int n = Math.min(buffer.remaining(), maximum - bytes.size());
                byte[] copy = new byte[n]; buffer.get(copy); bytes.writeBytes(copy);
                if (buffer.hasRemaining()) {
                    exceeded = true;
                    cancel(); completed.completeExceptionally(new IOException("Response byte capacity exceeded; captured prefix retained")); return;
                }
            }
            if (subscription != null) subscription.request(1);
        }
        @Override public synchronized void onError(Throwable error) { completed.completeExceptionally(error); }
        @Override public synchronized void onComplete() { completed.complete(bytes.toByteArray()); }
        synchronized void cancel() { if (subscription != null) subscription.cancel(); }
        synchronized byte[] snapshot() { return bytes.toByteArray(); }
        synchronized boolean exceeded() { return exceeded; }
    }
}
