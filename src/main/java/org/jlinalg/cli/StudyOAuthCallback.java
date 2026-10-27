/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.cli;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

/** One-use loopback-only callback. Invalid/unsolicited requests never cancel the pending login. */
final class StudyOAuthCallback implements AutoCloseable {
    private final HttpServer server;
    private final CompletableFuture<Map<String,String>> result=new CompletableFuture<>();
    private final String state;
    private final String path;
    StudyOAuthCallback(int port,String state) throws IOException {
        this(port,state,"/auth/callback");
    }
    StudyOAuthCallback(int port,String state,String path) throws IOException {
        if(!path.equals("/")&&!path.equals("/auth/callback"))throw new IOException("Unsupported OAuth callback path");
        this.state=state;this.path=path;
        server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),0);
        server.createContext("/",this::receive);
        server.start();
    }
    URI redirect() { return URI.create("http://127.0.0.1:"+server.getAddress().getPort()+path); }
    private void receive(HttpExchange request) throws IOException {
        int status=400;String message="Invalid login callback. Return to the original OmicsMatic login.";
        try {
            if(!request.getRequestMethod().equals("GET")) {status=405;}
            else if(!request.getRequestURI().getRawPath().equals(path)) {status=404;}
            else if(!("127.0.0.1:"+server.getAddress().getPort()).equals(request.getRequestHeaders().getFirst("Host"))
                    &&!(server.getAddress().getPort()==80&&"127.0.0.1".equals(request.getRequestHeaders().getFirst("Host")))) {status=400;}
            else {
                var values=StudyOAuth.query(request.getRequestURI().getRawQuery());
                if(StudyOAuth.equal(state,values.get("state"))&&(values.containsKey("code")^values.containsKey("error"))) {
                    if(result.complete(values)) {
                        status=200;message="Authorization response received. Close this tab and return to OmicsMatic to check whether credentials were saved.";
                    } else {status=409;message="This login callback has already been received.";}
                }
            }
        } catch(IOException|RuntimeException invalid) {status=400;}
        byte[] body=message.getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8");
        request.getResponseHeaders().set("Cache-Control","no-store");
        request.getResponseHeaders().set("Referrer-Policy","no-referrer");
        request.getResponseHeaders().set("Content-Security-Policy","default-src 'none'; frame-ancestors 'none'");
        try {
            request.sendResponseHeaders(status,body.length);
            request.getResponseBody().write(body);
        } finally {request.close();}
    }
    Map<String,String> await(Duration timeout) throws IOException,InterruptedException {
        try {return result.get(timeout.toMillis(),TimeUnit.MILLISECONDS);}
        catch(TimeoutException timeoutError) {throw new IOException("OAuth login timed out; no credentials saved. Start a fresh login");}
        catch(ExecutionException failure) {throw new IOException("OAuth callback unavailable; no credentials saved");}
    }
    @Override public void close() {server.stop(0);result.cancel(true);}
}
