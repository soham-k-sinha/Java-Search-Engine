package SearchEngine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UMassLocalAdapter implements Callable<List<Document>> {

    private static final String HTML_DIR = "data/umass-html/";
    private static final String SOURCE_NAME = "UMass Off-Campus Housing";
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$([\\d,]+)");
    private static final Pattern BEDS_PATTERN = Pattern.compile("(\\d+)\\s*Bed");
    private static final Pattern LEASE_PATTERN = Pattern.compile(
            "(\\d+\\s*Month\\s*Lease|Semester|Academic Year|Month-to-Month)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISTANCE_PATTERN = Pattern.compile(
            "([\\d.]+)\\s*miles?\\s*to\\s*UMass", Pattern.CASE_INSENSITIVE);

    @Override
    public List<Document> call() {
        List<Document> listings = new ArrayList<>();
        File dir = new File(HTML_DIR);

        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("[UMassLocal] No saved HTML directory at " + HTML_DIR);
            System.out.println("[UMassLocal] To use this adapter:");
            System.out.println("[UMassLocal]   1. Create data/umass-html/");
            System.out.println("[UMassLocal]   2. Open offcampushousing.umass.edu in your browser");
            System.out.println("[UMassLocal]   3. Save each page as HTML (Ctrl+S / Cmd+S)");
            System.out.println("[UMassLocal]   4. Name files page-1.html, page-2.html, etc.");
            return listings;
        }

        File[] htmlFiles = dir.listFiles((d, name) -> name.endsWith(".html"));
        if (htmlFiles == null || htmlFiles.length == 0) {
            System.out.println("[UMassLocal] No .html files found in " + HTML_DIR);
            return listings;
        }

        System.out.println("[UMassLocal] Found " + htmlFiles.length + " saved HTML files");

        for (File file : htmlFiles) {
            try {
                String html = Files.readString(file.toPath());
                org.jsoup.nodes.Document page = Jsoup.parse(html);
                int before = listings.size();
                parseListings(page, listings);
                int added = listings.size() - before;
                System.out.println("[UMassLocal] " + file.getName() + ": parsed " + added + " listings");
            } catch (IOException e) {
                System.err.println("[UMassLocal] Error reading " + file.getName() + ": " + e.getMessage());
            }
        }

        System.out.println("[UMassLocal] Total listings parsed: " + listings.size());
        return listings;
    }

    private void parseListings(org.jsoup.nodes.Document page, List<Document> listings) {
        Elements propertyLinks = page.select("h2 > a[href*=/housing/property/]");

        for (Element link : propertyLinks) {
            try {
                String name = link.text().trim();
                String propertyUrl = link.attr("abs:href");
                if (propertyUrl.isEmpty()) {
                    propertyUrl = "https://offcampushousing.umass.edu" + link.attr("href");
                }

                Element card = link.closest("li");
                if (card == null) {
                    card = link.parent();
                    if (card != null) card = card.parent();
                    if (card != null) card = card.parent();
                }
                if (card == null) continue;

                String cardText = card.text();

                String address = extractAddress(card);

                int price = 0;
                Matcher priceMatcher = PRICE_PATTERN.matcher(cardText);
                if (priceMatcher.find()) {
                    price = Integer.parseInt(priceMatcher.group(1).replace(",", ""));
                }

                int bedrooms = 0;
                Matcher bedsMatcher = BEDS_PATTERN.matcher(cardText);
                if (bedsMatcher.find()) {
                    bedrooms = Integer.parseInt(bedsMatcher.group(1));
                }
                if (cardText.contains("Studio")) {
                    bedrooms = 0;
                }

                String leaseTerms = "";
                Matcher leaseMatcher = LEASE_PATTERN.matcher(cardText);
                if (leaseMatcher.find()) {
                    leaseTerms = leaseMatcher.group(1);
                }

                String distance = "";
                Matcher distMatcher = DISTANCE_PATTERN.matcher(cardText);
                if (distMatcher.find()) {
                    distance = distMatcher.group(1) + " miles to UMass";
                }

                String phone = "";
                Elements telLinks = card.select("a[href^=tel:]");
                if (!telLinks.isEmpty()) {
                    String tel = telLinks.first().attr("href").replace("tel:", "").replace("+1", "");
                    if (tel.length() == 10) {
                        phone = "(" + tel.substring(0, 3) + ") " + tel.substring(3, 6) + "-" + tel.substring(6);
                    } else {
                        phone = tel;
                    }
                }

                String description = name;
                if (!distance.isEmpty()) {
                    description += ". " + distance + ".";
                }

                Document doc = new Document(
                        SOURCE_NAME,
                        propertyUrl,
                        address,
                        bedrooms,
                        price,
                        0,
                        description,
                        leaseTerms,
                        phone,
                        ""
                );
                listings.add(doc);

            } catch (Exception e) {
                System.err.println("[UMassLocal] Error parsing listing: " + e.getMessage());
            }
        }
    }

    private String extractAddress(Element card) {
        String cardText = card.text();

        Matcher addrMatcher = Pattern.compile(
                "(\\d+\\s+[A-Z][A-Za-z.]+(?:\\s+[A-Za-z.]+){0,3},\\s*[A-Za-z\\s]+,\\s*MA\\s*\\d{5})"
        ).matcher(cardText);
        if (addrMatcher.find()) {
            return addrMatcher.group(1).trim();
        }

        Matcher simpleMatcher = Pattern.compile(
                "(\\d+\\s+[A-Z][A-Za-z.]+[^,]{0,40},\\s*MA\\s*\\d{5})"
        ).matcher(cardText);
        if (simpleMatcher.find()) {
            return simpleMatcher.group(1).trim();
        }

        Matcher cityOnly = Pattern.compile("([A-Z][a-z]+,\\s*MA\\s*\\d{5})").matcher(cardText);
        if (cityOnly.find()) {
            return cityOnly.group(1).trim();
        }

        return "";
    }

    public static void main(String[] args) throws Exception {
        UMassLocalAdapter adapter = new UMassLocalAdapter();
        List<Document> results = adapter.call();
        System.out.println("\n=== SAMPLE RESULTS ===");
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            System.out.println(results.get(i));
            System.out.println("---");
        }
    }
}
