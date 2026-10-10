package com.filevault.filevaultserver.storage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A minimal in-memory stand-in for an S3-compatible endpoint, path-style ({@code /bucket/key}): PUT,
 * GET, HEAD and DELETE of whole objects. It does NOT verify signatures, so it proves the application's
 * flow and the SDK plumbing, not that R2 accepts the signed requests. Used by the unit tests and, via
 * {@link #main}, for running the real application end to end without a Cloudflare account.
 */
public class FakeS3Server implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private volatile int forcedStatus = 0;

    public FakeS3Server(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String endpoint() {
        return "http://127.0.0.1:" + port();
    }

    /** Pre-seed an object, keyed by its path ("/bucket/key"). */
    public void put(String path, byte[] content) {
        objects.put(path, content);
    }

    public boolean contains(String path) {
        return objects.containsKey(path);
    }

    public int objectCount() {
        return objects.size();
    }

    /** Make every following request fail with this status (0 = behave normally again). */
    public void failWith(int status) {
        this.forcedStatus = status;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (forcedStatus != 0) {
                exchange.sendResponseHeaders(forcedStatus, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            switch (exchange.getRequestMethod()) {
                case "PUT" -> {
                    objects.put(path, exchange.getRequestBody().readAllBytes());
                    exchange.getResponseHeaders().set("ETag", "\"fake\"");
                    exchange.sendResponseHeaders(200, -1);
                }
                case "HEAD" -> {
                    byte[] content = objects.get(path);
                    if (content == null) {
                        exchange.sendResponseHeaders(404, -1);
                    } else {
                        exchange.getResponseHeaders().set("Content-Length", String.valueOf(content.length));
                        exchange.sendResponseHeaders(200, -1);
                    }
                }
                case "GET" -> {
                    byte[] content = objects.get(path);
                    if (content == null) {
                        exchange.sendResponseHeaders(404, -1);
                    } else {
                        exchange.sendResponseHeaders(200, content.length);
                        exchange.getResponseBody().write(content);
                    }
                }
                case "DELETE" -> {
                    objects.remove(path);
                    exchange.sendResponseHeaders(204, -1);
                }
                default -> exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public static void main(String[] args) throws Exception {
        FakeS3Server fake = new FakeS3Server(Integer.parseInt(args[0]));
        System.out.println("Fake S3 listening on " + fake.endpoint());
        Thread.currentThread().join();
    }
}
