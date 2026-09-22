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
 * Crawls offcampushousing.umass.edu for UMass Amherst off-campus listings.
 * 
 * Site structure (CoStar/Off Campus Partners platform):
 * - Base URL: /housing/campus-UMass+Amherst_hjyxmy2
 * - Pagination: /page-2, /page-3, ... (20 listings per page)
 * - Each listing card is an <li> inside an <ol>
 * - Card contains: h2 > a (name + link), address, price, beds, lease term, distance, phone
 * - Most data is on the list page; no need to fetch individual listing pages.
 */
public class UMassAdapter implements Callable<List<Document>> {

    private static final String BASE_URL = "https://offcampushousing.umass.edu";
    private static final String FIRST_PAGE = BASE_URL + "/housing/campus-UMass+Amherst_hjyxmy2";
    private static final String SOURCE_NAME = "UMass Off-Campus Housing";
    private static final int DELAY_MS = 1000;

    // Patterns for extracting structured data from card text
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$([\\d,]+)");
    private static final Pattern BEDS_PATTERN = Pattern.compile("(\\d+)\\s*Bed");
    private static final Pattern LEASE_PATTERN = Pattern.compile("(\\d+\\s*Month\\s*Lease|Semester|Academic Year|Month-to-Month)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISTANCE_PATTERN = Pattern.compile("([\\d.]+)\\s*miles?\\s*to\\s*UMass", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}");

    // Session cookies collected from the first request
    private Map<String, String> cookies = new HashMap<>();

    @Override
    public List<Document> call() {
        List<Document> listings = new ArrayList<>();
        System.out.println("[UMass] Starting crawl...");

        try {
            // Hit the homepage first to establish a session and collect cookies
            initSession();

            // Fetch first page to figure out how many pages exist
            org.jsoup.nodes.Document firstPage = fetchPage(FIRST_PAGE);
            if (firstPage == null) return listings;

            // Parse first page listings
            parseListings(firstPage, listings);

            // Find pagination links to determine page count
            // The page links look like: a[href*=page-] with text "page 2", etc.
            Elements pageLinks = firstPage.select("a[href*=page-]");
            int maxPage = 1;
            for (Element link : pageLinks) {
                String href = link.attr("href");
                Matcher m = Pattern.compile("page-(\\d+)").matcher(href);
                if (m.find()) {
                    int pageNum = Integer.parseInt(m.group(1));
                    maxPage = Math.max(maxPage, pageNum);
                }
            }

            System.out.println("[UMass] Found " + maxPage + " pages total.");

            // Crawl remaining pages
            for (int page = 2; page <= maxPage; page++) {
                Thread.sleep(DELAY_MS);
                String url = FIRST_PAGE + "/page-" + page;
                org.jsoup.nodes.Document doc = fetchPage(url);
                if (doc != null) {
                    int before = listings.size();
                    parseListings(doc, listings);
                    System.out.println("[UMass] Page " + page + ": parsed " + (listings.size() - before) + " listings");
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[UMass] Crawl interrupted.");
        }

        System.out.println("[UMass] Total listings crawled: " + listings.size());
        return listings;
    }

    /**
     * Hits the homepage to collect session cookies before crawling listing pages.
     */
    private void initSession() {
        try {
            Connection.Response response = Jsoup.connect(BASE_URL)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .timeout(15000)
                    .execute();
            System.out.println("[UMass] Homepage status: " + response.statusCode());
            if (response.statusCode() == 403) {
                System.out.println("[UMass] 403 body: " + response.body().substring(0, Math.min(500, response.body().length())));
                System.out.println("[UMass] This site blocks automated requests. Try opening it in your browser:");
                System.out.println("[UMass]   https://offcampushousing.umass.edu/housing");
                System.out.println("[UMass] If it loads in your browser, the site uses JavaScript challenges that Jsoup can't handle.");
                return;
            }
            cookies = response.cookies();
            System.out.println("[UMass] Session established, got " + cookies.size() + " cookies");
        } catch (Exception e) {
            System.err.println("[UMass] Failed to init session: " + e.getMessage());
        }
    }

    /**
     * Fetches a page with Jsoup, passing along session cookies.
     */
    private org.jsoup.nodes.Document fetchPage(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .referrer(BASE_URL + "/housing")
                    .cookies(cookies)
                    .followRedirects(true)
                    .timeout(15000)
                    .get();
        } catch (Exception e) {
            System.err.println("[UMass] Failed to fetch: " + url + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses all listing cards from one page and adds Documents to the list.
     * 
     * Each listing card is inside the main content area. The property name is in
     * an h2 > a tag, and the card text contains address, price, beds, etc.
     */
    private void parseListings(org.jsoup.nodes.Document page, List<Document> listings) {
        // Select all h2 elements that contain a link to a property page.
        // Property links look like: /housing/property/{slug}/{id}
        Elements propertyLinks = page.select("h2 > a[href*=/housing/property/]");

        for (Element link : propertyLinks) {
            try {
                String name = link.text().trim();
                String propertyUrl = link.attr("abs:href");

                // Walk up to the card container. The h2's parent chain leads to
                // the <li> that contains all the card data.
                Element card = link.closest("li");
                if (card == null) {
                    // Fallback: try parent's parent
                    card = link.parent();
                    if (card != null) card = card.parent();
                }
                if (card == null) continue;

                String cardText = card.text();

                // --- Address ---
                // Address is usually right after the h2, inside the card.
                // It contains a state abbreviation and ZIP code like "Amherst, MA 01002"
                String address = extractAddress(card);

                // --- Price ---
                int price = 0;
                Matcher priceMatcher = PRICE_PATTERN.matcher(cardText);
                if (priceMatcher.find()) {
                    price = Integer.parseInt(priceMatcher.group(1).replace(",", ""));
                }

                // --- Bedrooms ---
                int bedrooms = 0;
                Matcher bedsMatcher = BEDS_PATTERN.matcher(cardText);
                if (bedsMatcher.find()) {
                    bedrooms = Integer.parseInt(bedsMatcher.group(1));
                }
                // Handle "Studio" as 0 bedrooms
                if (cardText.contains("Studio")) {
                    bedrooms = 0;
                }

                // --- Lease Term ---
                String leaseTerms = "";
                Matcher leaseMatcher = LEASE_PATTERN.matcher(cardText);
                if (leaseMatcher.find()) {
                    leaseTerms = leaseMatcher.group(1);
                }

                // --- Distance ---
                String distance = "";
                Matcher distMatcher = DISTANCE_PATTERN.matcher(cardText);
                if (distMatcher.find()) {
                    distance = distMatcher.group(1) + " miles to UMass";
                }

                // --- Phone ---
                String phone = "";
                // Phone is in a tel: link
                Elements telLinks = card.select("a[href^=tel:]");
                if (!telLinks.isEmpty()) {
                    String tel = telLinks.first().attr("href").replace("tel:", "").replace("+1", "");
                    // Format as (XXX) XXX-XXXX
                    if (tel.length() == 10) {
                        phone = "(" + tel.substring(0, 3) + ") " + tel.substring(3, 6) + "-" + tel.substring(6);
                    } else {
                        phone = tel;
                    }
                }

                // --- Description ---
                // The listing name serves as the description since there's no
                // detailed text on the list page.
                String description = name;
                if (!distance.isEmpty()) {
                    description += ". " + distance + ".";
                }

                // --- Amenities ---
                // Not available on list page cards
                String amenities = "";

                Document doc = new Document(
                        SOURCE_NAME,
                        propertyUrl,
                        address,
                        bedrooms,
                        price,
                        0, // bathrooms not consistently shown
                        description,
                        leaseTerms,
                        phone,
                        amenities
                );
                listings.add(doc);

            } catch (Exception e) {
                System.err.println("[UMass] Error parsing listing: " + e.getMessage());
            }
        }
    }

    /**
     * Extracts the address from a listing card.
     * Looks for text matching a MA address pattern (contains ", MA" or a 5-digit ZIP).
     * Falls back to text directly after the h2 element.
     */
    private String extractAddress(Element card) {
        // Try to find an element whose text looks like a MA address
        // Address lines typically contain "MA" and a ZIP code
        String cardText = card.text();
        Matcher addrMatcher = Pattern.compile("(\\d+[^,]+,\\s*[A-Za-z\\s]+,\\s*MA\\s*\\d{5})").matcher(cardText);
        if (addrMatcher.find()) {
            return addrMatcher.group(1).trim();
        }

        // Simpler fallback: look for just "City, MA XXXXX" pattern
        Matcher simpleMatcher = Pattern.compile("([^,]+,\\s*MA\\s*\\d{5})").matcher(cardText);
        if (simpleMatcher.find()) {
            return simpleMatcher.group(1).trim();
        }

        // Last resort: grab the text element right after the h2
        Element h2 = card.selectFirst("h2");
        if (h2 != null) {
            Element next = h2.nextElementSibling();
            if (next != null) {
                String text = next.text().trim();
                if (!text.isEmpty() && text.length() < 200) {
                    return text;
                }
            }
        }

        return "";
    }

    // For standalone testing
    public static void main(String[] args) throws Exception {
        UMassAdapter adapter = new UMassAdapter();
        List<Document> results = adapter.call();
        System.out.println("\n=== SAMPLE RESULTS ===");
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            System.out.println(results.get(i));
            System.out.println("---");
        }
    }
}