package SearchEngine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;

public class Document {
    private String address;
    private String amenities;
    private String contactInfo;
    private String leaseTerms;
    private String sourceName;
    private String sourceUrl;
    private String rawDescription;
    private int bedrooms;
    private int price;
    private int bathrooms;
    private int docId;

    private static int count = 0;

    // To make it persistent and not overwrite files every time it runs.
    static {
        File dir = new File("data/listings/");
        int maxCount = -1;
        
        File[] files = dir.listFiles();

        if (files != null) {
            for (File f : files) {
                String name = f.getName().replace(".txt", "");
                try {
                    maxCount = Math.max(maxCount, Integer.parseInt(name));
                } catch (NumberFormatException e) {
                    System.out.println("Not a number");
                }
            }
        }

        count = maxCount + 1;
    }

    public Document(String sourceName, String sourceUrl, String address, int bedrooms, int price, 
        int bathrooms, String rawDescription, String leaseTerms, String contactInfo, String amenities) {
            this.address = address;
            this.amenities = amenities;
            this.contactInfo = contactInfo;
            this.leaseTerms = leaseTerms;
            this.sourceName = sourceName;
            this.sourceUrl = sourceUrl;
            this.rawDescription = rawDescription;
            this.bedrooms = bedrooms;
            this.price = price;
            this.bathrooms = bathrooms;
            
            this.docId = count;
            count++;
    }

    @Override
    public String toString() {
        String template = """
    Doc ID: {0}
    Source Name: {1}
    Address: {2}
    Price: {3}
    Source URL: {4}
    Raw Description: {5}
    # of Bedrooms: {6}
    # of Bathrooms: {7}
    Contact Info: {8}
    Lease terms: {9}
    Amenities: {10}""";
        String content = MessageFormat.format(template, docId, sourceName, address, price, sourceUrl, 
            rawDescription, bedrooms, bathrooms, contactInfo, leaseTerms, amenities);
        return content;
    }

    public void addListing() {
        Path dir = Paths.get("data/listings/");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Path path = Paths.get("data/listings/" + this.docId + ".txt");
        String content = this.toString();

        try {
            Files.writeString(path, content);
            System.out.println("Document " + docId + " created.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getSearchableText() {
        String concat = "{0} {1} {2} {3} {4} {5} {6}";
        String content = MessageFormat.format(concat, sourceName, address, price, rawDescription, bedrooms, bathrooms, amenities);

        return content;
    }

    public int getDocId() {
        return this.docId;
    }

    public static void main(String[] args) {
        Document doc1 = new Document("north apartments", "www.", "north of here", 3, 2000, 2, 
        "Good rooms, very spacious. On bus route 33.", 
        "don't run", "gmail.com", "pool"
        );

        Document doc2 = new Document("boulders", "www.boulders", "bold", 3, 4000, 2, 
        "Cozy studio on bus route 31, all utilities included. Recently renovated kitchen with dishwasher. Quiet neighborhood near Atkins Farms",
        "don't run", "boulders@gmail.com", "spa"
        );

        Document doc3 = new Document("towns", "www.towns", "town", 3, 9000, 2, 
        "laundry near campus. Parking nearby", "pay on time", "town@gmail.com", "gym, trees"
        );

        Document doc4 = new Document("oberoi", "www.oberoi", "hadley", 1, 2000, 1, 
        "Spacious 2 bedroom apartment with in-unit washer dryer, hardwood floors, five minute walk to UMass campus. Heat and hot water included. Off-street parking available.", 
        "fun", "oberoi@gmail.com", "gym"
        );

        Document doc5 = new Document("esquire", "www.esquire", "north", 2, 3500, 2, 
        "Small rooms, great park. Amazing garden for kids.", "good", "esquire@gmail.com", "park"
        );

        doc1.addListing();
        doc2.addListing();
        doc3.addListing();
        doc4.addListing();
        doc5.addListing();
    }

}
