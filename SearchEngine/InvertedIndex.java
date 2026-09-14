package SearchEngine;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class InvertedIndex {
    static class Posting implements java.io.Serializable {
        public int docId;
        public int termFrequency;

        public Posting(int docId, int tF) {
            this.docId = docId;
            this.termFrequency = tF;
        }
    }

    private static final Set<String> STOP_WORDS = Set.of(
        "the", "a", "an", "is", "are", "was", "were", "in", "on",
        "at", "to", "for", "of", "and", "or", "with", "from", "by",
        "this", "that", "it", "as", "be", "has", "have", "had"
    );

    public HashMap<String, ArrayList<Posting>> index;
    public HashMap<String, Double> idf;
    public int docCount;

    public InvertedIndex() {
        index = new HashMap<>();
        idf = new HashMap<>();
        docCount = 0;
    }

    public void addDocument(Document doc) {
        String concatText = doc.getSearchableText();
        List<String> tokenized = tokenize(concatText);
        HashMap<String, Integer> termFrequencies = computeTermFrequency(tokenized);

        for (String token : termFrequencies.keySet()) {
            Posting p = new Posting(doc.getDocId(), termFrequencies.get(token));
            if (this.index.containsKey(token)) {
                this.index.get(token).add(p);
            } else {
                ArrayList<Posting> list = new ArrayList<>();
                list.add(p);
                this.index.put(token, list);
            }
        }

        this.docCount++;
    }

    public List<String> tokenize(String concat) {
        concat = concat.toLowerCase();
        ArrayList<String> unFilteredTokens = new ArrayList<>(Arrays.asList(concat.split("[\\p{Punct}\\s]+")));
        return unFilteredTokens.stream().filter(t -> !STOP_WORDS.contains(t)).toList();
    }

    public HashMap<String, Integer> computeTermFrequency(List<String> tokens) {
        HashMap<String, Integer> counter = new HashMap<>();
        for (String s : tokens) {
            counter.put(s, counter.getOrDefault(s, 0) + 1);
        }
        return counter;
    }

    public void computeIdf() {
        for (String s : this.index.keySet()) {
            this.idf.put(s, (double) Math.log(this.docCount / this.index.get(s).size()));
        }
    }

    public List<Integer> search(String query) {
        List<String> tokenizedQuery = tokenize(query);
        record TfIdf(int docId, double score) {}
        List<TfIdf> scoredPostings = new ArrayList<>();

        for (String token : tokenizedQuery) {
            if (this.index.containsKey(token)) {
                for (Posting p : this.index.get(token)) {
                    double score = (p.termFrequency * this.idf.get(token));
                    scoredPostings.add(new TfIdf(p.docId, score));
                }
            }
        }
        
        HashMap<Integer, Double> documentScores = new HashMap<>();
        for (TfIdf i : scoredPostings) {
            if (documentScores.containsKey(i.docId)) {
                documentScores.put(i.docId, documentScores.get(i.docId) + i.score);
            } else {
                documentScores.put(i.docId, i.score);
            }
        }

        List<Map.Entry<Integer, Double>> sorted = new ArrayList<>(documentScores.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Integer> relevantDocs = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : sorted) {
            relevantDocs.add(entry.getKey());
        }

        return relevantDocs;
    }

    public void serialize(String filepath) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filepath))) {
            out.writeObject(this.index);
            out.writeObject(this.idf);
            out.writeInt(this.docCount);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static InvertedIndex deserialize(String filepath) {
        InvertedIndex idx = new InvertedIndex();
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filepath))) {
            idx.index = (HashMap<String, ArrayList<Posting>>) in.readObject();
            idx.idf = (HashMap<String, Double>) in.readObject();
            idx.docCount = in.readInt();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return idx;
    }

    public String printIndex() {
        return "";
    }

    public static void main(String[] args) {
        InvertedIndex i = new InvertedIndex();

        Document doc1 = new Document("north apartments", "www.", "north of here", 3, 2000, 2, 
        "Good rooms, very spacious. On bus route 33.", 
        "don't run", "gmail.com", "pool"
        );

        Document doc2 = new Document("boulders", "www.boulders", "bold", 3, 4000, 2, 
        "Cozy studio on bus route 31, all utilities included. Recently renovated kitchen with dishwasher. Quiet neighborhood near Atkins Farms",
        "don't run", "boulders@gmail.com", "spa"
        );

        i.addDocument(doc1);
        i.addDocument(doc2);
        i.computeIdf();
        // i.serialize("SearchEngine/data/index.txt");
        InvertedIndex i2 = InvertedIndex.deserialize("SearchEngine/data/index.txt");
        System.out.println(i.docCount);
        System.out.println(i2.docCount);
        System.out.println(i.search("bus 31"));
        System.out.println(i2.search("bus 31"));
        System.out.println(i.index);
        System.out.println(i2.index);
    }
}
