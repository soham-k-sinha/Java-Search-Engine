package SearchEngine;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final InvertedIndex index;
    private final int port;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(8);

    public Server(int port, InvertedIndex index) {
        this.port = port;
        this.index = index;
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
        System.out.println("Server running on http://0.0.0.0:" + port);

        while (true) {
            Socket client = serverSocket.accept();
            threadPool.submit(() -> {
                try {
                    handleRequest(client);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try { client.close(); } catch (IOException ignored) {}
                }
            });
        }
    }

    private void handleRequest(Socket client) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        String requestLine = in.readLine();

        if (requestLine == null || requestLine.isEmpty()) return;

        String[] parts = requestLine.split(" ");
        if (parts.length < 2) return;

        String method = parts[0];
        String fullPath = parts[1];

        if (method.equals("OPTIONS")) {
            sendCorsPreflightResponse(client);
            return;
        }

        String path;
        Map<String, String> queryParams = new HashMap<>();

        if (fullPath.contains("?")) {
            path = fullPath.substring(0, fullPath.indexOf("?"));
            String queryString = fullPath.substring(fullPath.indexOf("?") + 1);
            queryParams = parseQueryParams(queryString);
        } else {
            path = fullPath;
        }

        if (path.equals("/")) {
            serveStaticFile(client, "SearchEngine/static/index.html", "text/html");
        } else if (path.equals("/search")) {
            handleSearch(client, queryParams);
        } else if (path.startsWith("/listing/")) {
            String idStr = path.substring("/listing/".length());
            handleListing(client, idStr);
        } else {
            sendResponse(client, 404, "application/json", "{\"error\": \"Not found\"}");
        }
    }

    private Map<String, String> parseQueryParams(String queryString) {
        Map<String, String> params = new HashMap<>();
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    private void handleSearch(Socket client, Map<String, String> params) throws IOException {
        String query = params.getOrDefault("q", "");
        int limit = Integer.parseInt(params.getOrDefault("limit", "10"));

        if (query.isEmpty()) {
            sendResponse(client, 400, "application/json", "{\"error\": \"Missing query parameter 'q'\"}");
            return;
        }

        List<Integer> docIds = index.search(query);

        if (docIds.size() > limit) {
            docIds = docIds.subList(0, limit);
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"query\": \"").append(escapeJson(query)).append("\", \"results\": [");

        boolean first = true;
        for (int docId : docIds) {
            String docJson = documentToJson(docId);
            if (docJson == null) continue;
            if (!first) json.append(", ");
            json.append(docJson);
            first = false;
        }

        json.append("]}");
        sendResponse(client, 200, "application/json", json.toString());
    }

    private void handleListing(Socket client, String idStr) throws IOException {
        try {
            int docId = Integer.parseInt(idStr);
            String json = documentToJson(docId);
            if (json != null) {
                sendResponse(client, 200, "application/json", json);
            } else {
                sendResponse(client, 404, "application/json", "{\"error\": \"Listing not found\"}");
            }
        } catch (NumberFormatException e) {
            sendResponse(client, 400, "application/json", "{\"error\": \"Invalid listing ID\"}");
        }
    }

    private String documentToJson(int docId) {
        File file = new File("data/listings/" + docId + ".txt");
        if (!file.exists()) return null;

        try {
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            Map<String, String> fields = new HashMap<>();
            for (String line : content.split("\n")) {
                line = line.trim();
                if (line.contains(":")) {
                    int colonIndex = line.indexOf(":");
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    fields.put(key, value);
                }
            }

            return String.format(
                "{\"docId\": %d, \"address\": \"%s\", \"price\": \"%s\", \"bedrooms\": \"%s\", " +
                "\"bathrooms\": \"%s\", \"source\": \"%s\", \"sourceUrl\": \"%s\", " +
                "\"description\": \"%s\", \"amenities\": \"%s\", \"leaseTerms\": \"%s\", " +
                "\"contactInfo\": \"%s\"}",
                docId,
                escapeJson(fields.getOrDefault("Address", "")),
                escapeJson(fields.getOrDefault("Price", "")),
                escapeJson(fields.getOrDefault("# of Bedrooms", "")),
                escapeJson(fields.getOrDefault("# of Bathrooms", "")),
                escapeJson(fields.getOrDefault("Source Name", "")),
                escapeJson(fields.getOrDefault("Source URL", "")),
                escapeJson(fields.getOrDefault("Raw Description", "")),
                escapeJson(fields.getOrDefault("Amenities", "")),
                escapeJson(fields.getOrDefault("Lease terms", "")),
                escapeJson(fields.getOrDefault("Contact Info", ""))
            );
        } catch (IOException e) {
            return null;
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private void serveStaticFile(Socket client, String filePath, String contentType) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            sendResponse(client, 404, "text/plain", "File not found");
            return;
        }
        byte[] fileBytes = Files.readAllBytes(path);

        OutputStream out = client.getOutputStream();
        String headers = "HTTP/1.1 200 OK\r\n"
            + "Content-Type: " + contentType + "; charset=utf-8\r\n"
            + "Content-Length: " + fileBytes.length + "\r\n"
            + "Access-Control-Allow-Origin: *\r\n"
            + "\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.flush();
    }

    private void sendCorsPreflightResponse(Socket client) throws IOException {
        OutputStream out = client.getOutputStream();
        String response = "HTTP/1.1 204 No Content\r\n"
            + "Access-Control-Allow-Origin: *\r\n"
            + "Access-Control-Allow-Methods: GET, OPTIONS\r\n"
            + "Access-Control-Allow-Headers: Content-Type\r\n"
            + "\r\n";
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void sendResponse(Socket client, int statusCode, String contentType, String body) throws IOException {
        String statusText;
        switch (statusCode) {
            case 200: statusText = "OK"; break;
            case 400: statusText = "Bad Request"; break;
            case 404: statusText = "Not Found"; break;
            default: statusText = "Internal Server Error"; break;
        }

        OutputStream out = client.getOutputStream();
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n"
            + "Content-Type: " + contentType + "\r\n"
            + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
            + "Access-Control-Allow-Origin: *\r\n"
            + "\r\n"
            + body;
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public static void main(String[] args) throws IOException {
        InvertedIndex idx = new InvertedIndex();

        Document doc1 = new Document("Kamins", "https://kamins.com/123",
            "123 Main St, Amherst", 2, 1800, 1,
            "Spacious 2 bedroom apartment with in-unit washer dryer, hardwood floors. Five minute walk to UMass campus. Heat and hot water included. Off-street parking available.",
            "12 month lease", "413-555-0101", "parking, laundry, hardwood floors");

        Document doc2 = new Document("UMass Housing", "https://housing.umass.edu/456",
            "456 North Pleasant St", 1, 1200, 1,
            "Cozy studio on bus route 31, all utilities included. Recently renovated kitchen with dishwasher. Quiet neighborhood near Atkins Farms.",
            "9 month lease", "413-555-0202", "utilities included, dishwasher");

        idx.addDocument(doc1);
        idx.addDocument(doc2);
        doc1.addListing();
        doc2.addListing();
        idx.computeIdf();

        Server server = new Server(8080, idx);
        server.start();
    }
}
