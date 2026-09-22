package SearchEngine;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Crawls valleypm.com for Valley Property Management rental listings.
 * 
 * Site structure (WordPress + Estatik plugin):
 * - All listings on one page: /property/
 * - No pagination needed (~6-15 listings)
 * - Each card has: price, h3 > a (title + link), address, beds, baths, description snippet
 * - Individual listing pages at /property/{slug}/ have rich detail:
 *   amenities, lease dates, pet policy, parking, laundry, description, agent contact
 * - Default contact: 413-225-1105
 */
public class ValleyPMAdapter implements Callable<List<Document>> {

    private static final String BASE_URL = "https://valleypm.com";
    private static final String LIST_URL = BASE_URL + "/property/";
    private static final String SOURCE_NAME = "Valley Property Management";
    private static final String DEFAULT_PHONE = "(413) 225-1105";
    private static final int DELAY_MS = 1000;

    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$([\\d,]+)");
    private static final Pattern BEDS_PATTERN = Pattern.compile("(\\d+)\\s*beds?", Pattern.CASE_INSENSITIVE);
    private static final Pattern BATHS_PATTERN = Pattern.compile("(\\d+)\\s*baths?", Pattern.CASE_INSENSITIVE);

    private Map<String, String> cookies = new HashMap<>();

    @Override
    public List<Document> call() {
        List<Document> listings = new ArrayList<>();
        System.out.println("[ValleyPM] Starting crawl...");

        try {
            // Init session cookies
            try {
                Connection.Response resp = Jsoup.connect(BASE_URL)
                        .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .followRedirects(true).timeout(15000).execute();
                cookies = resp.cookies();
            } catch (Exception e) {
                System.err.println("[ValleyPM] Cookie init failed: " + e.getMessage());
            }

            org.jsoup.nodes.Document page = fetchPage(LIST_URL);
            if (page == null) return listings;

            // Parse listing cards from the main /property/ page
            parseListPage(page, listings);
            System.out.println("[ValleyPM] Found " + listings.size() + " listings on main page.");

            // Fetch each individual listing page for detailed info
            // We store the URLs before iterating since we'll rebuild Document objects
            List<String[]> detailUrls = new ArrayList<>();
            for (Document doc : listings) {
                // We need the URL. Since Document doesn't expose it with a getter,
                // we store it during parsing. See parseListPage - it stores URL in rawDescription
                // temporarily. This is a workaround.
            }

            // Detail page enrichment is handled inline during parseListPage since
            // we have the URLs at parse time.

        } catch (Exception e) {
            System.err.println("[ValleyPM] Crawl error: " + e.getMessage());
        }

        System.out.println("[ValleyPM] Total listings crawled: " + listings.size());
        return listings;
    }

    private org.jsoup.nodes.Document fetchPage(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .referrer(BASE_URL)
                    .cookies(cookies)
                    .followRedirects(true)
                    .timeout(15000)
                    .get();
        } catch (Exception e) {
            System.err.println("[ValleyPM] Failed to fetch: " + url + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses listing cards from valleypm.com/property/.
     * 
     * Each listing card has this structure (from the Estatik plugin):
     * - Price text: "$1,500 per month"
     * - h3 > a with the listing title and URL to /property/{slug}/
     * - Address text (e.g., "462 Main St unit 31, Amherst, MA 01002, USA")
     * - Description snippet
     * - Beds/baths with bold numbers: "**1** bed", "**1** bath"
     * 
     * After parsing the list page, we fetch each detail page for richer data.
     */
    private void parseListPage(org.jsoup.nodes.Document page, List<Document> listings) {
        // Find all property title links. These are in h3 tags inside listing cards.
        // Property URLs look like /property/{slug}/
        Elements propertyLinks = page.select("h3 > a[href*=/property/]");

        // Collect URLs and basic info first, then fetch detail pages
        List<String[]> cardData = new ArrayList<>(); // [name, url, address, price, beds, baths, snippet]

        for (Element link : propertyLinks) {
            try {
                String name = link.text().trim();
                String propertyUrl = link.attr("abs:href");

                // Skip non-listing links (like "View Details" links)
                if (name.equals("View Details") || name.equals("View on map") || name.isEmpty()) {
                    continue;
                }

                // Walk up to the card container
                Element card = link.closest("div");
                // Keep going up until we have a div with enough content
                while (card != null && card.text().length() < 100) {
                    Element parent = card.parent();
                    if (parent != null && parent.tagName().equals("div")) {
                        card = parent;
                    } else {
                        break;
                    }
                }
                if (card == null) continue;

                String cardText = card.text();

                // --- Price ---
                int price = 0;
                // Look for price near "per month" text
                Matcher priceMatcher = PRICE_PATTERN.matcher(cardText);
                if (priceMatcher.find()) {
                    price = Integer.parseInt(priceMatcher.group(1).replace(",", ""));
                }

                // --- Address ---
                // Address is usually in a separate element with text containing "MA" and a ZIP
                String address = "";
                for (Element el : card.getAllElements()) {
                    String text = el.ownText().trim();
                    if (text.matches(".*\\d+.*,.*MA.*\\d{5}.*")) {
                        address = text.replaceAll(",\\s*USA\\s*$", "").trim();
                        break;
                    }
                }

                // --- Beds and Baths ---
                int bedrooms = 0;
                int bathrooms = 0;
                Matcher bedsMatcher = BEDS_PATTERN.matcher(cardText);
                if (bedsMatcher.find()) {
                    bedrooms = Integer.parseInt(bedsMatcher.group(1));
                }
                Matcher bathsMatcher = BATHS_PATTERN.matcher(cardText);
                if (bathsMatcher.find()) {
                    bathrooms = Integer.parseInt(bathsMatcher.group(1));
                }

                // --- Description snippet from list page ---
                String snippet = "";
                // The snippet is usually the first paragraph-like text that isn't the title or address
                for (Element el : card.select("p, div")) {
                    String text = el.ownText().trim();
                    if (text.length() > 30 && !text.equals(name) && !text.contains("per month")
                            && !text.contains("View Details")) {
                        snippet = text;
                        break;
                    }
                }

                cardData.add(new String[]{
                        name, propertyUrl, address, String.valueOf(price),
                        String.valueOf(bedrooms), String.valueOf(bathrooms), snippet
                });

            } catch (Exception e) {
                System.err.println("[ValleyPM] Error parsing card: " + e.getMessage());
            }
        }

        // Deduplicate by URL (the page may have duplicate links per listing)
        List<String> seenUrls = new ArrayList<>();
        List<String[]> uniqueCards = new ArrayList<>();
        for (String[] data : cardData) {
            if (!seenUrls.contains(data[1])) {
                seenUrls.add(data[1]);
                uniqueCards.add(data);
            }
        }

        // Now fetch detail pages and create Documents
        for (String[] data : uniqueCards) {
            try {
                Thread.sleep(DELAY_MS);

                String name = data[0];
                String propertyUrl = data[1];
                String address = data[2];
                int price = Integer.parseInt(data[3]);
                int bedrooms = Integer.parseInt(data[4]);
                int bathrooms = Integer.parseInt(data[5]);
                String snippet = data[6];

                // Fetch detail page for rich data
                String description = snippet;
                String leaseTerms = "";
                String amenities = "";
                String contactInfo = DEFAULT_PHONE;

                org.jsoup.nodes.Document detailPage = fetchPage(propertyUrl);
                if (detailPage != null) {
                    DetailData detail = parseDetailPage(detailPage);
                    if (!detail.description.isEmpty()) description = detail.description;
                    if (!detail.leaseTerms.isEmpty()) leaseTerms = detail.leaseTerms;
                    if (!detail.amenities.isEmpty()) amenities = detail.amenities;
                    if (!detail.contact.isEmpty()) contactInfo = detail.contact;
                    // Update beds/baths/address from detail page if we got better data
                    if (detail.bedrooms > 0) bedrooms = detail.bedrooms;
                    if (detail.bathrooms > 0) bathrooms = detail.bathrooms;
                    if (!detail.address.isEmpty()) address = detail.address;
                }

                Document doc = new Document(
                        SOURCE_NAME,
                        propertyUrl,
                        address,
                        bedrooms,
                        price,
                        bathrooms,
                        description,
                        leaseTerms,
                        contactInfo,
                        amenities
                );
                listings.add(doc);

                System.out.println("[ValleyPM] Parsed: " + name);

            } catch (Exception e) {
                System.err.println("[ValleyPM] Error enriching listing: " + e.getMessage());
            }
        }
    }

    /**
     * Container for data extracted from a detail page.
     */
    private static class DetailData {
        String description = "";
        String leaseTerms = "";
        String amenities = "";
        String contact = "";
        String address = "";
        int bedrooms = 0;
        int bathrooms = 0;
    }

    /**
     * Parses a Valley PM individual listing page for rich data.
     * 
     * Detail pages have sections for:
     * - Description text (the main listing description)
     * - Property details (beds, baths, property type)
     * - Rent includes info (what's included in rent)
     * - Agent contact info
     */
    private DetailData parseDetailPage(org.jsoup.nodes.Document page) {
        DetailData data = new DetailData();

        try {
            String fullText = page.text();

            // --- Description ---
            // Look for the main description. It's usually in the article body or
            // a div with the listing content. Skip boilerplate.
            Elements contentBlocks = page.select("article p, .entry-content p, .property_description p, .es-property-desc p");
            StringBuilder desc = new StringBuilder();
            for (Element p : contentBlocks) {
                String text = p.text().trim();
                if (text.isEmpty()) continue;
                // Skip boilerplate
                if (text.contains("Listings advertised may no longer") ||
                        text.contains("Alan St. Hilaire") ||
                        text.contains("broker for property") ||
                        text.contains("Privacy Policy") ||
                        text.length() < 15) {
                    continue;
                }
                if (desc.length() > 0) desc.append(" ");
                desc.append(text);
                // Cap at a reasonable length
                if (desc.length() > 500) break;
            }
            data.description = desc.toString();

            // --- Beds and Baths from detail page ---
            Matcher bedsMatcher = BEDS_PATTERN.matcher(fullText);
            if (bedsMatcher.find()) {
                data.bedrooms = Integer.parseInt(bedsMatcher.group(1));
            }
            Matcher bathsMatcher = BATHS_PATTERN.matcher(fullText);
            if (bathsMatcher.find()) {
                data.bathrooms = Integer.parseInt(bathsMatcher.group(1));
            }

            // --- Address ---
            // Often in the page title or a specific element
            Element addressEl = page.selectFirst("address, .es-address, [itemprop=streetAddress]");
            if (addressEl != null) {
                data.address = addressEl.text().trim().replaceAll(",\\s*USA\\s*$", "");
            }

            // --- Lease terms ---
            // Look for "Payments Required" or lease info
            if (fullText.contains("Payments Required:")) {
                int idx = fullText.indexOf("Payments Required:");
                data.leaseTerms = fullText.substring(idx, Math.min(idx + 150, fullText.length()));
                // Trim to end of sentence
                int period = data.leaseTerms.indexOf('.');
                if (period > 0) data.leaseTerms = data.leaseTerms.substring(0, period + 1);
            }

            // --- Rent Includes ---
            if (fullText.contains("Rent Includes:")) {
                int idx = fullText.indexOf("Rent Includes:");
                String includes = fullText.substring(idx, Math.min(idx + 200, fullText.length()));
                int nextSection = includes.indexOf("Available");
                if (nextSection > 0) includes = includes.substring(0, nextSection);
                data.amenities = includes.trim();
            }

            // --- Contact ---
            Elements telLinks = page.select("a[href^=tel:]");
            if (!telLinks.isEmpty()) {
                data.contact = telLinks.first().text().trim();
                if (data.contact.isEmpty()) {
                    data.contact = telLinks.first().attr("href").replace("tel:", "");
                }
            }
            // Also check for email
            Elements mailLinks = page.select("a[href^=mailto:]");
            for (Element mail : mailLinks) {
                String email = mail.text().trim();
                if (email.contains("@") && !email.contains("valleypm413")) {
                    if (!data.contact.isEmpty()) data.contact += " / ";
                    data.contact += email;
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("[ValleyPM] Error parsing detail page: " + e.getMessage());
        }

        return data;
    }

    public static void main(String[] args) throws Exception {
        ValleyPMAdapter adapter = new ValleyPMAdapter();
        List<Document> results = adapter.call();
        System.out.println("\n=== ALL RESULTS ===");
        for (Document doc : results) {
            System.out.println(doc);
            System.out.println("---");
        }
    }
}