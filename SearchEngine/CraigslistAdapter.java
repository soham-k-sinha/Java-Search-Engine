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

public class CraigslistAdapter implements Callable<List<Document>> {

    private static final String LIST_URL = "https://www.craigslist.org/search/city/amherst-ma?cat=apa";
    private static final String SOURCE_NAME = "Craigslist";
    private static final int DELAY_MS = 800;

    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$([\\d,]+)");
    private static final Pattern BEDS_PATTERN = Pattern.compile("(?<![.\\d])(\\d+)\\s*BR", Pattern.CASE_INSENSITIVE);
    private static final Pattern BATHS_PATTERN = Pattern.compile("(?<![.\\d])(\\d+(?:\\.\\d+)?)\\s*Ba", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCATION_PATTERN = Pattern.compile("\\(([^)]+)\\)");

    @Override
    public List<Document> call() {
        List<Document> listings = new ArrayList<>();
        System.out.println("[Craigslist] Starting crawl...");

        try {
            org.jsoup.nodes.Document listPage = fetchPage(LIST_URL);
            if (listPage == null) {
                System.err.println("[Craigslist] Failed to fetch list page");
                return listings;
            }

            List<String[]> cards = new ArrayList<>();
            Set<String> seenUrls = new HashSet<>();

            Elements items = listPage.select("li.cl-static-search-result");
            System.out.println("[Craigslist] Found " + items.size() + " listing cards");

            for (Element item : items) {
                Element link = item.selectFirst("a[href]");
                if (link == null) continue;

                String url = link.attr("abs:href");
                if (url.isEmpty() || seenUrls.contains(url)) continue;
                seenUrls.add(url);

                Element titleEl = item.selectFirst(".title");
                String title = (titleEl != null) ? titleEl.text().trim() : item.attr("title").trim();
                if (title.isEmpty()) continue;

                Element priceEl = item.selectFirst(".price");
                String priceText = (priceEl != null) ? priceEl.text().trim() : "";

                Element locationEl = item.selectFirst(".location");
                String location = (locationEl != null) ? locationEl.text().trim() : "";

                cards.add(new String[]{title, url, priceText, location});
            }

            System.out.println("[Craigslist] Unique cards: " + cards.size() + ". Fetching detail pages...");

            for (String[] card : cards) {
                String title = card[0];
                String url = card[1];
                String priceText = card[2];
                String location = card[3];

                Thread.sleep(DELAY_MS);

                int price = 0;
                Matcher priceMatcher = PRICE_PATTERN.matcher(priceText);
                if (priceMatcher.find()) {
                    price = Integer.parseInt(priceMatcher.group(1).replace(",", ""));
                }

                int bedrooms = 0;
                int bathrooms = 0;
                String description = title;
                String address = location;
                String amenities = "";
                String leaseTerms = "";
                String contactInfo = "";

                org.jsoup.nodes.Document detailPage = fetchPage(url);
                if (detailPage != null) {
                    DetailData detail = parseDetailPage(detailPage);
                    if (!detail.description.isEmpty()) description = detail.description;
                    if (detail.bedrooms > 0) bedrooms = detail.bedrooms;
                    if (detail.bathrooms > 0) bathrooms = detail.bathrooms;
                    if (detail.price > 0 && price == 0) price = detail.price;
                    if (!detail.address.isEmpty()) address = detail.address;
                    if (!detail.amenities.isEmpty()) amenities = detail.amenities;
                    if (!detail.leaseTerms.isEmpty()) leaseTerms = detail.leaseTerms;
                    if (!detail.contactInfo.isEmpty()) contactInfo = detail.contactInfo;
                } else {
                    Matcher bedsMatcher = BEDS_PATTERN.matcher(title);
                    if (bedsMatcher.find()) bedrooms = Integer.parseInt(bedsMatcher.group(1));
                    if (title.toLowerCase().contains("studio")) bedrooms = 0;
                }

                if (address.isEmpty()) address = location;
                if (address.isEmpty()) address = "Amherst area";

                Document doc = new Document(
                        SOURCE_NAME,
                        url,
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
                System.out.println("[Craigslist] Added: " + title.substring(0, Math.min(50, title.length())));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Craigslist] Crawl interrupted.");
        }

        System.out.println("[Craigslist] Total listings crawled: " + listings.size());
        return listings;
    }

    private static class DetailData {
        String description = "";
        String address = "";
        String amenities = "";
        String leaseTerms = "";
        String contactInfo = "";
        int bedrooms = 0;
        int bathrooms = 0;
        int price = 0;
    }

    private DetailData parseDetailPage(org.jsoup.nodes.Document page) {
        DetailData data = new DetailData();

        try {
            Element postingBody = page.selectFirst("#postingbody");
            if (postingBody != null) {
                Element qrDiv = postingBody.selectFirst(".print-qrcode-container");
                if (qrDiv != null) qrDiv.remove();

                String rawText = postingBody.html()
                        .replaceAll("<br\\s*/?>", "\n")
                        .replaceAll("<[^>]+>", "")
                        .trim();
                rawText = rawText.replaceAll("&amp;", "&")
                        .replaceAll("&lt;", "<")
                        .replaceAll("&gt;", ">")
                        .replaceAll("&quot;", "\"")
                        .replaceAll("&#x27;", "'")
                        .replaceAll("&nbsp;", " ");

                StringBuilder desc = new StringBuilder();
                for (String line : rawText.split("\n")) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (desc.length() > 0) desc.append(" ");
                    desc.append(line);
                }
                data.description = desc.toString();
                if (data.description.length() > 1000) {
                    data.description = data.description.substring(0, 1000);
                }
            }

            Element h1 = page.selectFirst("h1");
            if (h1 != null) {
                String h1Text = h1.text();
                Matcher priceMatcher = PRICE_PATTERN.matcher(h1Text);
                if (priceMatcher.find()) {
                    data.price = Integer.parseInt(priceMatcher.group(1).replace(",", ""));
                }
                Matcher locMatcher = LOCATION_PATTERN.matcher(h1Text);
                if (locMatcher.find()) {
                    data.address = locMatcher.group(1).trim();
                }
            }

            Elements attrGroups = page.select(".attrgroup");
            List<String> amenityList = new ArrayList<>();

            for (Element group : attrGroups) {
                String groupText = group.text().trim();

                Matcher bedsMatcher = BEDS_PATTERN.matcher(groupText);
                if (bedsMatcher.find()) {
                    data.bedrooms = Integer.parseInt(bedsMatcher.group(1));
                }
                Matcher bathsMatcher = BATHS_PATTERN.matcher(groupText);
                if (bathsMatcher.find()) {
                    data.bathrooms = (int) Math.ceil(Double.parseDouble(bathsMatcher.group(1)));
                }

                Elements attrs = group.select(".attr");
                for (Element attr : attrs) {
                    String attrText = attr.text().trim().toLowerCase();
                    if (attrText.contains("w/d") || attrText.contains("laundry") ||
                            attrText.contains("parking") || attrText.contains("pet") ||
                            attrText.contains("furnished") || attrText.contains("no smoking") ||
                            attrText.contains("ev charging") || attrText.contains("gym") ||
                            attrText.contains("pool") || attrText.contains("air conditioning")) {
                        amenityList.add(attr.text().trim());
                    }
                    if (attrText.contains("rent period")) {
                        data.leaseTerms = attr.text().trim();
                    }
                }
            }
            if (!amenityList.isEmpty()) {
                data.amenities = String.join(", ", amenityList);
            }

            Elements replyLinks = page.select("a[href^=mailto:]");
            if (!replyLinks.isEmpty()) {
                data.contactInfo = replyLinks.first().attr("href").replace("mailto:", "");
            }

        } catch (Exception e) {
            System.err.println("[Craigslist] Error parsing detail page: " + e.getMessage());
        }

        return data;
    }

    private org.jsoup.nodes.Document fetchPage(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .followRedirects(true)
                    .timeout(15000)
                    .get();
        } catch (Exception e) {
            System.err.println("[Craigslist] Failed to fetch: " + url + " - " + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        CraigslistAdapter adapter = new CraigslistAdapter();
        List<Document> results = adapter.call();
        System.out.println("\n=== SAMPLE RESULTS ===");
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            System.out.println(results.get(i));
            System.out.println("---");
        }
    }
}
