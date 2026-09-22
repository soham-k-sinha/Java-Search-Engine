package SearchEngine;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KaminsAdapter implements Callable<List<Document>> {

    private static final String BASE_URL = "https://kaminsrealestate.com";
    private static final String LIST_URL = BASE_URL + "/for-rent-residential/";
    private static final String SOURCE_NAME = "Kamins Real Estate";
    private static final String DEFAULT_PHONE = "(413) 253-2515";
    private static final int DELAY_MS = 500;
    private static final int MAX_PAGES = 10;

    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$([\\d,]+)");

    private Map<String, String> cookies = new HashMap<>();

    private static class CardData {
        String name;
        String url;
        String address;
        int price;
        int bedrooms;
        int bathrooms;
    }

    @Override
    public List<Document> call() {
        List<Document> listings = new ArrayList<>();
        System.out.println("[Kamins] Starting crawl...");

        try {
            try {
                Connection.Response resp = Jsoup.connect(BASE_URL)
                        .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .followRedirects(true).timeout(15000).execute();
                cookies = resp.cookies();
            } catch (Exception e) {
                System.err.println("[Kamins] Cookie init failed: " + e.getMessage());
            }

            List<CardData> cards = new ArrayList<>();
            Set<String> seenUrls = new HashSet<>();
            int currentPage = 1;

            while (currentPage <= MAX_PAGES) {
                String url = (currentPage == 1) ? LIST_URL : LIST_URL + "page/" + currentPage + "/";
                org.jsoup.nodes.Document page = fetchPage(url);
                if (page == null) break;

                int before = cards.size();
                collectCards(page, cards, seenUrls);
                int added = cards.size() - before;

                System.out.println("[Kamins] Page " + currentPage + ": found " + added + " listings");

                if (added == 0) break;

                Elements nextLink = page.select("a:containsOwn(Next Page)");
                if (nextLink.isEmpty()) break;

                currentPage++;
                Thread.sleep(DELAY_MS);
            }

            System.out.println("[Kamins] Total cards: " + cards.size());

            for (CardData card : cards) {
                String description = card.name;
                if (card.bedrooms > 0) {
                    description += ". " + card.bedrooms + " bedroom";
                    if (card.bathrooms > 0) {
                        description += ", " + card.bathrooms + " bathroom";
                    }
                    description += " rental in the Amherst area.";
                }
                if (card.price > 0) {
                    description += " $" + card.price + "/month.";
                }

                String amenities = "";

                Document doc = new Document(
                        SOURCE_NAME,
                        card.url,
                        card.address,
                        card.bedrooms,
                        card.price,
                        card.bathrooms,
                        description,
                        "",
                        DEFAULT_PHONE,
                        amenities
                );
                listings.add(doc);
                System.out.println("[Kamins] Added: " + card.name);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Kamins] Crawl interrupted.");
        }

        System.out.println("[Kamins] Total listings crawled: " + listings.size());
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
            System.err.println("[Kamins] Failed to fetch: " + url + " - " + e.getMessage());
            return null;
        }
    }

    private void collectCards(org.jsoup.nodes.Document page, List<CardData> cards, Set<String> seenUrls) {
        Elements propertyLinks = page.select("h3 > a[href*=/rental/]");

        for (Element link : propertyLinks) {
            try {
                String name = link.text().trim();
                String propertyUrl = link.attr("abs:href");

                if (seenUrls.contains(propertyUrl)) continue;
                seenUrls.add(propertyUrl);

                Element card = link.parent();
                if (card != null) card = card.parent();
                if (card != null && card.text().length() < 50) {
                    card = card.parent();
                }
                if (card == null) continue;

                String cardText = card.text();

                String address = name;
                Elements townLinks = card.select("a[href*=/rental/]");
                for (Element tl : townLinks) {
                    String townText = tl.text().trim();
                    if (!townText.equals(name) && townText.matches(".*\\b[A-Z]{2}\\b.*")) {
                        address = name + ", " + townText;
                        break;
                    }
                }

                int price = 0;
                Matcher priceMatcher = PRICE_PATTERN.matcher(cardText);
                if (priceMatcher.find()) {
                    price = Integer.parseInt(priceMatcher.group(1).replace(",", ""));
                }

                int bedrooms = 0;
                int bathrooms = 0;
                List<String> smallNumbers = new ArrayList<>();
                for (Element el : card.select("*")) {
                    String t = el.ownText().trim();
                    if (t.matches("\\d+\\.?\\d*") && t.length() <= 3) {
                        smallNumbers.add(t);
                    }
                }
                if (smallNumbers.size() >= 1) {
                    bedrooms = (int) Double.parseDouble(smallNumbers.get(0));
                }
                if (smallNumbers.size() >= 2) {
                    bathrooms = (int) Double.parseDouble(smallNumbers.get(1));
                }

                CardData cd = new CardData();
                cd.name = name;
                cd.url = propertyUrl;
                cd.address = address;
                cd.price = price;
                cd.bedrooms = bedrooms;
                cd.bathrooms = bathrooms;
                cards.add(cd);

            } catch (Exception e) {
                System.err.println("[Kamins] Error parsing card: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        KaminsAdapter adapter = new KaminsAdapter();
        List<Document> results = adapter.call();
        System.out.println("\n=== SAMPLE RESULTS ===");
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            System.out.println(results.get(i));
            System.out.println("---");
        }
    }
}
