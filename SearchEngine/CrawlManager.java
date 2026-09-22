package SearchEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CrawlManager {

    private static final int THREAD_POOL_SIZE = 4;
    private static final String INDEX_PATH = "data/index.ser";
    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) {
        System.out.println("=== Housing Search Engine - CrawlManager ===\n");

        clearListings();

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try {
            System.out.println("[CrawlManager] Submitting crawl tasks...");
            Future<List<Document>> kamins = pool.submit(new KaminsAdapter());
            Future<List<Document>> valley = pool.submit(new ValleyPMAdapter());
            Future<List<Document>> craigslist = pool.submit(new CraigslistAdapter());
            Future<List<Document>> umassLocal = pool.submit(new UMassLocalAdapter());

            List<Document> allListings = new ArrayList<>();

            System.out.println("[CrawlManager] Waiting for Kamins adapter...");
            List<Document> kaminsResults = kamins.get();
            allListings.addAll(kaminsResults);
            System.out.println("[CrawlManager] Kamins: " + kaminsResults.size() + " listings");

            System.out.println("[CrawlManager] Waiting for ValleyPM adapter...");
            List<Document> valleyResults = valley.get();
            allListings.addAll(valleyResults);
            System.out.println("[CrawlManager] ValleyPM: " + valleyResults.size() + " listings");

            System.out.println("[CrawlManager] Waiting for Craigslist adapter...");
            List<Document> craigslistResults = craigslist.get();
            allListings.addAll(craigslistResults);
            System.out.println("[CrawlManager] Craigslist: " + craigslistResults.size() + " listings");

            System.out.println("[CrawlManager] Waiting for UMass local adapter...");
            List<Document> umassResults = umassLocal.get();
            allListings.addAll(umassResults);
            System.out.println("[CrawlManager] UMass Local: " + umassResults.size() + " listings");

            System.out.println("\n[CrawlManager] Raw total: " + allListings.size() + " listings");

            List<Document> deduped = deduplicate(allListings);
            System.out.println("[CrawlManager] After deduplication: " + deduped.size() + " listings");

            InvertedIndex index = new InvertedIndex();

            for (Document doc : deduped) {
                doc.addListing();
                index.addDocument(doc);
            }

            index.computeIdf();
            index.serialize(INDEX_PATH);
            System.out.println("[CrawlManager] Index built and serialized to " + INDEX_PATH);
            System.out.println("[CrawlManager] Index contains " + index.docCount + " documents, " + index.index.size() + " unique terms");

            System.out.println("\n[CrawlManager] Starting server on port " + SERVER_PORT + "...");
            Server server = new Server(SERVER_PORT, index);
            server.start();

        } catch (Exception e) {
            System.err.println("[CrawlManager] Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }

    private static void clearListings() {
        File dir = new File("data/listings/");
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                int count = 0;
                for (File f : files) {
                    if (f.isFile() && f.delete()) {
                        count++;
                    }
                }
                System.out.println("[CrawlManager] Cleared " + count + " old listing files");
            }
        }
    }

    private static List<Document> deduplicate(List<Document> listings) {
        HashMap<String, Document> seen = new HashMap<>();
        int dupeCount = 0;

        for (Document doc : listings) {
            String key = buildDeduplicationKey(doc);

            if (seen.containsKey(key)) {
                dupeCount++;
                Document existing = seen.get(key);
                if (doc.getSearchableText().length() > existing.getSearchableText().length()) {
                    seen.put(key, doc);
                }
            } else {
                seen.put(key, doc);
            }
        }

        System.out.println("[CrawlManager] Removed " + dupeCount + " duplicate listings");
        return new ArrayList<>(seen.values());
    }

    private static String buildDeduplicationKey(Document doc) {
        String text = doc.toString();

        String address = "";
        String bedrooms = "";
        String price = "";

        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.startsWith("Address:")) {
                address = line.substring("Address:".length()).trim();
            } else if (line.startsWith("# of Bedrooms:")) {
                bedrooms = line.substring("# of Bedrooms:".length()).trim();
            } else if (line.startsWith("Price:")) {
                price = line.substring("Price:".length()).trim();
            }
        }

        String normalized = address.toLowerCase();
        normalized = normalized.replaceAll("\\s+", " ").trim();

        normalized = normalized.replaceAll("\\bstreet\\b", "st");
        normalized = normalized.replaceAll("\\broad\\b", "rd");
        normalized = normalized.replaceAll("\\bavenue\\b", "ave");
        normalized = normalized.replaceAll("\\bdrive\\b", "dr");
        normalized = normalized.replaceAll("\\blane\\b", "ln");
        normalized = normalized.replaceAll("\\bcourt\\b", "ct");
        normalized = normalized.replaceAll("\\bplace\\b", "pl");
        normalized = normalized.replaceAll("\\bcircle\\b", "cir");
        normalized = normalized.replaceAll("\\bboulevard\\b", "blvd");
        normalized = normalized.replaceAll("\\bnorth\\b", "n");
        normalized = normalized.replaceAll("\\bsouth\\b", "s");
        normalized = normalized.replaceAll("\\beast\\b", "e");
        normalized = normalized.replaceAll("\\bwest\\b", "w");

        normalized = normalized.replaceAll("\\b\\d{5}\\b", "");
        normalized = normalized.replaceAll("(unit|apt|suite|#)\\s*\\w+", "");
        normalized = normalized.replaceAll(",\\s*usa\\s*$", "");
        normalized = normalized.replaceAll("[^a-z0-9 ]", "");
        normalized = normalized.replaceAll("\\s+", " ").trim();

        int priceBucket = 0;
        try {
            priceBucket = Integer.parseInt(price) / 100;
        } catch (NumberFormatException ignored) {
        }

        return normalized + "|" + bedrooms + "|" + priceBucket;
    }
}
