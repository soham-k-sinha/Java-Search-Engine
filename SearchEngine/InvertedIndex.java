package SearchEngine;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

public class InvertedIndex {
    static class Posting {
        public int docId;
        public int termFrequency;

        public void InvertedIndex(int docId, int tF) {
            this.docId = docId;
            this.termFrequency = tF;
        }
    }

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
    }

    public List<String> tokenize(String concat) {
        return new ArrayList<String>();
    }

    public int computeTermFrequency(List<String> tokens) {
        return 0;
    }

    public Double computeIdf() {
        return 0.0;
    }

    public List<Integer> Search(String query) {
        return new ArrayList<Integer>();
    }

    public void serialize(String filepath) {

    }

    public void deserialize(String filepath) {

    }

    public String printIndex() {
        return "";
    }
}
