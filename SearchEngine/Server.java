package SearchEngine;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
    private final InvertedIndex index;
    private final int port;

    public Server(int port, InvertedIndex index) {
        this.port = port;
        this.index = index;
    }

    public void start() throws IOException {
        // Step 1: Open a port and listen for connections
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server running on http://localhost:" + port);

        // This loop runs forever, handling one request at a time
        while (true) {
            // accept() blocks until a browser/client connects
            Socket client = serverSocket.accept();

            try {
                handleRequest(client);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                client.close();
            }
        }
    }

    private void handleRequest(Socket client) throws IOException {
        // Step 2: Read the request line the browser sends
        // Browser sends something like: "GET /search?q=parking+campus&limit=5 HTTP/1.1"
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        String requestLine = in.readLine();

        if (requestLine == null || requestLine.isEmpty()) return;

        String[] parts = requestLine.split(" ");
        if (parts.length < 2) return;

        String method = parts[0];   // "GET"
        String fullPath = parts[1]; // "/search?q=parking+campus&limit=5"

        // Step 3: Split the path from the query string
        String path;
        Map<String, String> queryParams = new HashMap<>();

        if (fullPath.contains("?")) {
            path = fullPath.substring(0, fullPath.indexOf("?"));
            String queryString = fullPath.substring(fullPath.indexOf("?") + 1);
            queryParams = parseQueryParams(queryString);
        } else {
            path = fullPath;
        }

        // Step 4: Route to the right handler based on the path
        if (path.equals("/search")) {
            handleSearch(client, queryParams);
        } else if (path.startsWith("/listing/")) {
            String idStr = path.substring("/listing/".length());
            handleListing(client, idStr);
        } else {
            sendResponse(client, 404, "{\"error\": \"Not found\"}");
        }
    }

    // Parses "q=parking+campus&limit=5" into a map: {q: "parking campus", limit: "5"}
    private Map<String, String> parseQueryParams(String queryString) {
        Map<String, String> params = new HashMap<>();
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                // URLDecoder turns "parking+campus" back into "parking campus"
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
            sendResponse(client, 400, "{\"error\": \"Missing query parameter 'q'\"}");
            return;
        }

        // Use search method
        List<Integer> docIds = index.search(query);

        // Cap results at the limit
        if (docIds.size() > limit) {
            docIds = docIds.subList(0, limit);
        }

        // Build JSON response manually
        StringBuilder json = new StringBuilder();
        json.append("{\"query\": \"").append(query).append("\", \"results\": [");

        for (int i = 0; i < docIds.size(); i++) {
            int docId = docIds.get(i);
            // Load the listing file from disk and format as JSON
            json.append(documentToJson(docId));
            if (i < docIds.size() - 1) {
                json.append(", ");
            }
        }

        json.append("]}");
        sendResponse(client, 200, json.toString());
    }

    private void handleListing(Socket client, String idStr) throws IOException {
        try {
            int docId = Integer.parseInt(idStr);
            String json = documentToJson(docId);
            if (json != null) {
                sendResponse(client, 200, json);
            } else {
                sendResponse(client, 404, "{\"error\": \"Listing not found\"}");
            }
        } catch (NumberFormatException e) {
            sendResponse(client, 400, "{\"error\": \"Invalid listing ID\"}");
        }
    }

    // Reads a listing file from disk and returns it as a JSON string
    private String documentToJson(int docId) {
        File file = new File("data/listings/" + docId + ".txt");
        if (!file.exists()) return null;

        try {
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            // Parse the fields back out of the text file
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

            // Build JSON manually
            return String.format(
                "{\"docId\": %d, \"address\": \"%s\", \"price\": \"%s\", \"bedrooms\": \"%s\", " +
                "\"bathrooms\": \"%s\", \"source\": \"%s\", \"sourceUrl\": \"%s\", " +
                "\"description\": \"%s\", \"amenities\": \"%s\"}",
                docId,
                escapeJson(fields.getOrDefault("Address", "")),
                escapeJson(fields.getOrDefault("Price", "")),
                escapeJson(fields.getOrDefault("# of Bedrooms", "")),
                escapeJson(fields.getOrDefault("# of Bathrooms", "")),
                escapeJson(fields.getOrDefault("Source Name", "")),
                escapeJson(fields.getOrDefault("Source URL", "")),
                escapeJson(fields.getOrDefault("Raw Description", "")),
                escapeJson(fields.getOrDefault("Amenities", ""))
            );
        } catch (IOException e) {
            return null;
        }
    }

    // JSON breaks if your text contains quotes or newlines. This escapes them.
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    // Writes a complete HTTP response to the client
    private void sendResponse(Socket client, int statusCode, String body) throws IOException {
        String statusText;
        switch (statusCode) {
            case 200: statusText = "OK"; break;
            case 400: statusText = "Bad Request"; break;
            case 404: statusText = "Not Found"; break;
            default: statusText = "Internal Server Error"; break;
        }

        OutputStream out = client.getOutputStream();
        // HTTP response format: status line, headers, blank line, body
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n"
            + "Content-Type: application/json\r\n"
            + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
            + "\r\n"
            + body;
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public static void main(String[] args) throws IOException {
        // Build the index with your fake listings
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

        // Start the server
        Server server = new Server(8080, idx);
        server.start();
    }
}