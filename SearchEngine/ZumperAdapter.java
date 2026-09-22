package SearchEngine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZumperAdapter implements Callable<List<Document>> {

    private static final String SOURCE_NAME = "Zumper";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int DELAY_MS = 1200;

    private static final String[] TOWN_SLUGS = {
            "amherst-ma", "hadley-ma", "sunderland-ma",
            "northampton-ma", "belchertown-ma"
    };

    private static final Pattern BEDS_PATTERN = Pattern.compile("\"numberOfBedrooms\"\\s*:\\s*(\\d+)");
    private static final Pattern BATHS_VALUE = Pattern.compile("\"Bathrooms per unit\",\"value\":\"(\\d+(?:\\.\\d+)?)\"");
    private static final Pattern AMENITY_NAME = Pattern.compile("\"name\":\"([^\"]+)\"");
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$(\\d[\\d,]*)");

    @Override
    public List<Document> call() {
        List<Document> listings = new ArrayList<>();
        Set<String> seenAddresses = new HashSet<>();
        System.out.println("[Zumper] Starting crawl...");

        for (String townSlug : TOWN_SLUGS) {
            try {
                Thread.sleep(DELAY_MS);
                String url = "https://www.zumper.com/apartments-for-rent/" + townSlug;
                org.jsoup.nodes.Document page = fetchPage(url);
                if (page == null) continue;

                List<Document> townListings = parseListPage(page, seenAddresses);
                listings.addAll(townListings);
                System.out.println("[Zumper] " + townSlug + ": " + townListings.size() + " listings");

            } catch (Exception e) {
                System.out.println("[Zumper] Error on " + townSlug + ": " + e.getMessage());
            }
        }

        System.out.println("[Zumper] Total listings crawled: " + listings.size());
        return listings;
    }

    private org.jsoup.nodes.Document fetchPage(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .referrer("https://www.google.com/")
                    .followRedirects(true)
                    .timeout(15000)
                    .get();
        } catch (Exception e) {
            System.out.println("[Zumper] Failed to fetch: " + url + " - " + e.getMessage());
            return null;
        }
    }

    private List<Document> parseListPage(org.jsoup.nodes.Document page, Set<String> seenAddresses) {
        List<Document> results = new ArrayList<>();

        String jsonLd = "";
        for (Element s : page.select("script[type=application/ld+json]")) {
            String text = s.html();
            if (text.length() > 1000 && text.contains("itemListElement")) {
                jsonLd = text;
                break;
            }
        }
        if (jsonLd.isEmpty()) return results;

        List<int[]> itemPositions = findListItems(jsonLd);

        for (int[] bounds : itemPositions) {
            try {
                String chunk = jsonLd.substring(bounds[0], bounds[1]);
                Document doc = parseListingChunk(chunk, seenAddresses);
                if (doc != null) results.add(doc);
            } catch (Exception e) {
                // skip malformed entries
            }
        }

        return results;
    }

    private List<int[]> findListItems(String json) {
        List<int[]> positions = new ArrayList<>();
        String marker = "\"@type\":\"ListItem\"";
        int idx = 0;

        while (true) {
            int start = json.indexOf(marker, idx);
            if (start < 0) break;

            int braceStart = json.lastIndexOf("{", start);
            if (braceStart < 0) { idx = start + 1; continue; }

            int nextStart = json.indexOf(marker, start + marker.length());
            int end;
            if (nextStart > 0) {
                int nextBrace = json.lastIndexOf("{", nextStart);
                end = nextBrace > braceStart ? nextBrace : nextStart;
            } else {
                end = json.length();
            }

            positions.add(new int[]{braceStart, end});
            idx = start + marker.length();
        }

        return positions;
    }

    private Document parseListingChunk(String chunk, Set<String> seenAddresses) {
        String url = extractValue(chunk, "\"@id\":\"");
        if (url.isEmpty()) url = extractValue(chunk, "\"url\":\"");
        if (url.isEmpty()) return null;

        String name = "";
        int aboutIdx = chunk.indexOf("\"about\":");
        if (aboutIdx > 0) {
            String aboutSection = chunk.substring(aboutIdx);
            name = extractValue(aboutSection, "\"name\":\"");
        }
        if (name.isEmpty()) {
            name = extractValue(chunk, "\"name\":\"");
        }

        String street = extractValue(chunk, "\"streetAddress\":\"");
        String city = extractValue(chunk, "\"addressLocality\":\"");
        String zip = extractValue(chunk, "\"postalCode\":\"");
        String address = "";
        if (!street.isEmpty()) {
            address = street + ", " + city + ", MA " + zip;
        } else if (!city.isEmpty()) {
            address = city + ", MA " + zip;
        }

        String dedupKey = address.toLowerCase().replaceAll("\\s+", " ").trim();
        if (dedupKey.isEmpty() || seenAddresses.contains(dedupKey)) return null;
        seenAddresses.add(dedupKey);

        int bedrooms = 0;
        Matcher bedsM = BEDS_PATTERN.matcher(chunk);
        if (bedsM.find()) bedrooms = Integer.parseInt(bedsM.group(1));

        int bathrooms = 0;
        Matcher bathsM = BATHS_VALUE.matcher(chunk);
        if (bathsM.find()) bathrooms = (int) Math.ceil(Double.parseDouble(bathsM.group(1)));

        String phone = extractValue(chunk, "\"telephone\":\"");

        StringBuilder amenBuf = new StringBuilder();
        int amenStart = chunk.indexOf("\"amenityFeature\":[");
        if (amenStart > 0) {
            int amenEnd = chunk.indexOf("]", amenStart);
            if (amenEnd > amenStart) {
                String amenSection = chunk.substring(amenStart, amenEnd);
                Matcher amenM = AMENITY_NAME.matcher(amenSection);
                while (amenM.find()) {
                    String amenName = amenM.group(1);
                    if (!amenName.equals("Bathrooms per unit")) {
                        if (amenBuf.length() > 0) amenBuf.append(", ");
                        amenBuf.append(amenName);
                    }
                }
            }
        }

        String petsAllowed = extractValue(chunk, "\"petsAllowed\":\"");
        if (petsAllowed.equalsIgnoreCase("yes") && !amenBuf.toString().contains("Pet")) {
            if (amenBuf.length() > 0) amenBuf.append(", ");
            amenBuf.append("Pets Allowed");
        }

        String description = name;
        if (amenBuf.length() > 0) {
            description += ". Features: " + amenBuf;
        }

        System.out.println("[Zumper] Added: " + name);

        return new Document(
                SOURCE_NAME,
                url,
                address,
                bedrooms,
                0,
                bathrooms,
                description,
                "",
                phone,
                amenBuf.toString()
        );
    }

    private String extractValue(String json, String prefix) {
        int idx = json.indexOf(prefix);
        if (idx < 0) return "";
        int start = idx + prefix.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == '"') { sb.append('"'); i++; }
                else if (next == '\\') { sb.append('\\'); i++; }
                else if (next == 'n') { sb.append(' '); i++; }
                else { sb.append(c); }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    public static void main(String[] args) throws Exception {
        ZumperAdapter adapter = new ZumperAdapter();
        List<Document> results = adapter.call();
        System.out.println("\n=== SAMPLE RESULTS ===");
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            System.out.println(results.get(i));
            System.out.println("---");
        }
    }
}
