package SearchEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Orchestrates the full crawl pipeline:
 * 1. Submits adapter tasks concurrently (3-thread pool)
 * 2. Collects results from all adapters via Future.get()
 * 3. Deduplicates listings using normalized address + bedrooms + price bucket
 * 4. Creates Document objects, saves to disk
 * 5. Builds the InvertedIndex, computes IDF, serializes to data/index.ser
 * 6. Starts the HTTP Server on port 8080
 */
public class CrawlManager {

    private static final int THREAD_POOL_SIZE = 3;
    private static final String INDEX_PATH = "data/index.ser";
    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) {
        System.out.println("=== Housing Search Engine - CrawlManager ===\n");

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try {
            // 1. Submit all adapter tasks concurrently
            System.out.println("[CrawlManager] Submitting crawl tasks...");
            Future<List<Document>> umass = pool.submit(new UMassAdapter());
            Future<List<Document>> kamins = pool.submit(new KaminsAdapter());
            Future<List<Document>> valley = pool.submit(new ValleyPMAdapter());

            // 2. Collect results (Future.get() blocks until each task completes)
            List<Document> allListings = new ArrayList<>();

            System.out.println("[CrawlManager] Waiting for UMass adapter...");
            List<Document> umassResults = umass.get();
            allListings.addAll(umassResults);
            System.out.println("[CrawlManager] UMass: " + umassResults.size() + " listings");

            System.out.println("[CrawlManager] Waiting for Kamins adapter...");
            List<Document> kaminsResults = kamins.get();
            allListings.addAll(kaminsResults);
            System.out.println("[CrawlManager] Kamins: " + kaminsResults.size() + " listings");

            System.out.println("[CrawlManager] Waiting for ValleyPM adapter...");
            List<Document> valleyResults = valley.get();
            allListings.addAll(valleyResults);
            System.out.println("[CrawlManager] ValleyPM: " + valleyResults.size() + " listings");

            System.out.println("\n[CrawlManager] Raw total: " + allListings.size() + " listings");

            // 3. Deduplicate
            List<Document> deduped = deduplicate(allListings);
            System.out.println("[CrawlManager] After deduplication: " + deduped.size() + " listings");

            // 4. Save documents to disk and build index
            InvertedIndex index = new InvertedIndex();

            for (Document doc : deduped) {
                doc.addListing(); // saves to data/listings/{docId}.txt
                index.addDocument(doc);
            }

            // 5. Compute IDF and serialize the index
            index.computeIdf();
            index.serialize(INDEX_PATH);
            System.out.println("[CrawlManager] Index built and serialized to " + INDEX_PATH);
            System.out.println("[CrawlManager] Index contains " + index.docCount + " documents, " + index.index.size() + " unique terms");

            // 6. Start the server
            System.out.println("\n[CrawlManager] Starting server on port " + SERVER_PORT + "...");
            Server server = new Server(SERVER_PORT, index);
            server.start(); // blocks forever

        } catch (Exception e) {
            System.err.println("[CrawlManager] Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Deduplicates listings using a normalized key: address + bedrooms + price bucket.
     * 
     * The key is built by:
     * 1. Lowercasing the address
     * 2. Collapsing whitespace
     * 3. Normalizing street abbreviations (st/street, rd/road, etc.)
     * 4. Stripping ZIP codes and unit numbers
     * 5. Appending bedroom count
     * 6. Appending price bucket (rounded to nearest $100)
     * 
     * When duplicates are found, we keep the listing with more data (longer description).
     */
    private static List<Document> deduplicate(List<Document> listings) {
        // Map from dedup key to the best Document for that key
        HashMap<String, Document> seen = new HashMap<>();
        int dupeCount = 0;

        for (Document doc : listings) {
            String key = buildDeduplicationKey(doc);

            if (seen.containsKey(key)) {
                dupeCount++;
                // Keep the one with more searchable text (a proxy for more data)
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

    /**
     * Builds a dedup key from a Document's searchable text.
     * Since Document doesn't have individual field getters (besides docId and searchableText),
     * we extract address and numeric info from the searchable text.
     */
    private static String buildDeduplicationKey(Document doc) {
        String text = doc.getSearchableText().toLowerCase();

        // Extract what we can from the searchable text.
        // getSearchableText() concatenates: sourceName, address, price, rawDescription, bedrooms, bathrooms, amenities
        // We'll normalize the whole string for dedup purposes.

        String normalized = text;

        // Collapse whitespace
        normalized = normalized.replaceAll("\\s+", " ").trim();

        // Normalize common street abbreviations
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

        // Strip ZIP codes (5-digit numbers)
        normalized = normalized.replaceAll("\\b\\d{5}\\b", "");

        // Strip unit/apt numbers like "unit 2", "apt 3", "#4"
        normalized = normalized.replaceAll("(unit|apt|#)\\s*\\w+", "");

        // Strip everything except letters, digits, spaces
        normalized = normalized.replaceAll("[^a-z0-9 ]", "");

        // Collapse whitespace again after all the stripping
        normalized = normalized.replaceAll("\\s+", " ").trim();

        return normalized;
    }
}