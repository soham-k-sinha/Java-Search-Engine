package Learning.MultiThreading;

import java.util.concurrent.ConcurrentLinkedQueue;
public class MultiThread implements Runnable {
    public ConcurrentLinkedQueue<String> tracker;
    public MultiThread(ConcurrentLinkedQueue<String> tracker) {
        this.tracker = tracker;
    }
    
    @Override
    public void run() {
        for (int i = 1; i < 6; i++) {
            String curr = Thread.currentThread().getName();
            this.tracker.offer("https://www." + curr + "/" + i + ".com");
        }
    }
}
