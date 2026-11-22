/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.gprofiler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.gprofiler.GprofilerGostClient.*;

/** Bounded HTTP capture, adapted from the project's Enrichr transport. */
final class GprofilerHttpTransport implements Transport {
    private final HttpClient http;
    private final ExecutorService executor;
    GprofilerHttpTransport(Duration timeout) {
        executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "omicsmatic-gprofiler-http"); thread.setDaemon(true); return thread;
        });
        http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER)
            .executor(executor).version(HttpClient.Version.HTTP_1_1).build();
    }
    static final class InterruptedTransfer extends InterruptedException {
        private static final long serialVersionUID = 1L;
        @SuppressWarnings("serial") final Reply reply;
        InterruptedTransfer(Reply reply) { super("Interrupted transfer; captured prefix retained"); this.reply = reply; }
    }
    @Override public Reply send(Request request) throws InterruptedException {
        AtomicReference<HttpResponse.ResponseInfo> info = new AtomicReference<>();
        AtomicReference<LimitedBody> body = new AtomicReference<>();
        var wire = HttpRequest.newBuilder(request.uri()).timeout(request.timeout())
            .header("User-Agent", "OmicsMatic-gProfiler/1.0")
            .header("Accept", "application/json").header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofByteArray(request.body())).build();
        CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(wire, response -> {
            info.set(response); var subscriber = new LimitedBody(request.maximumResponseBytes());
            body.set(subscriber); return subscriber;
        });
        try {
            HttpResponse<byte[]> response = future.get(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
            return new Reply(response.statusCode(), response.headers().map(), response.body(), State.COMPLETE, null);
        } catch (InterruptedException error) {
            future.cancel(true); LimitedBody partial = body.get(); if (partial != null) partial.cancel();
            throw new InterruptedTransfer(partial(info.get(), partial, State.INTERRUPTED, "Interrupted; no complete response"));
        } catch (Exception error) {
            future.cancel(true); LimitedBody partial = body.get(); if (partial != null) partial.cancel();
            State state = partial != null && partial.exceeded() ? State.CAPACITY : State.UNAVAILABLE;
            for (Throwable cause = error; cause != null; cause = cause.getCause())
                if (cause instanceof TimeoutException || cause instanceof HttpTimeoutException) { state = State.TIMEOUT; break; }
            return partial(info.get(), partial, state, error.toString());
        }
    }
    private static Reply partial(HttpResponse.ResponseInfo info, LimitedBody body, State state, String reason) {
        return new Reply(info == null ? 0 : info.statusCode(), info == null ? Map.of() : info.headers().map(),
            body == null ? new byte[0] : body.snapshot(), state, reason);
    }
    @Override public void close() throws IOException {
        // Java 17 has no HttpClient.close(). Each send is complete or cancelled
        // before return; shut down only this transport's request executor.
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) throw new IOException("Own HTTP executor has not terminated; preserve diagnostics");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IOException("Interrupted while closing own HTTP client", error);
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
                int length = Math.min(buffer.remaining(), maximum - bytes.size());
                byte[] copy = new byte[length]; buffer.get(copy); bytes.writeBytes(copy);
                if (buffer.hasRemaining()) {
                    exceeded = true; cancel();
                    completed.completeExceptionally(new IOException("Response capacity exceeded; captured prefix retained")); return;
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
