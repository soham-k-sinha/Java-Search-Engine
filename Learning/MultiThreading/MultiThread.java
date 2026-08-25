package Learning.MultiThreading;

import java.util.List;
public class MultiThread implements Runnable {
    public List<String> tracker;
    public MultiThread(List<String> tracker) {
        this.tracker = tracker;
    }
    
    @Override
    public void run() {
        for (int i = 1; i < 6; i++) {
            String curr = Thread.currentThread().getName();
            this.tracker.add("https://www." + curr + "/" + i + ".com");
        }
    }
}
