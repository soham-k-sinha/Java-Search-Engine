package Learning.MultiThreading;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Pools {

    public static void main(String[] args) throws InterruptedException{
        List<String> urls = new ArrayList<>(Arrays.asList(
            "https://google.com",
            "https://wikipedia.org",
            "https://github.com",
            "https://reddit.com",
            "https://amazon.com",
            "https://apple.com",
            "https://microsoft.com",
            "https://youtube.com",
            "https://netflix.com",
            "https://linkedin.com",
            "https://twitter.com",
            "https://instagram.com",
            "https://facebook.com",
            "https://pinterest.com",
            "https://tumblr.com",
            "https://wordpress.org",
            "https://vimeo.com",
            "https://flickr.com",
            "https://imdb.com",
            "https://bbc.com",
            "https://cnn.com",
            "https://nytimes.com",
            "https://theguardian.com",
            "https://forbes.com",
            "https://bloomberg.com",
            "https://reuters.com",
            "https://wsj.com",
            "https://cnbc.com",
            "https://huffpost.com",
            "https://buzzfeed.com",
            "https://techcrunch.com",
            "https://wired.com",
            "https://theverge.com",
            "https://engadget.com",
            "https://mashable.com",
            "https://medium.com",
            "https://quora.com",
            "https://stackoverflow.com",
            "https://stackexchange.com",
            "https://github.io",
            "https://gitlab.com",
            "https://bitbucket.org",
            "https://sourceforge.net",
            "https://apache.org",
            "https://w3schools.com",
            "https://mozilla.org",
            "https://archive.org",
            "https://nasa.gov",
            "https://nih.gov",
            "https://weather.gov"
        )).stream().map(url -> {
                    if (url.contains("://www.")) {
                        return url;
                    }
                    return url.replace("://", "://www.");
                        }).collect(Collectors.toList());
        
        
        // Make the pool
        ExecutorService pool = Executors.newFixedThreadPool(5);
        HttpClient client = HttpClient.newHttpClient();
        
        for (String url: urls) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            pool.submit(() -> {
                try {
                    int response = client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
                    System.out.println(url + ": " + response);
                } catch (InterruptedException | IOException e) {
                     System.out.println(url + ": FAILED - " + e.getMessage());
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.MINUTES); 

    }
}
