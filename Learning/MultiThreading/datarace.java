package Learning.MultiThreading;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
public class DataRace {

    public static void main(String[] args) throws InterruptedException {
        // List<String> urls = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        ConcurrentLinkedQueue<String> urls = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(new MultiThread(urls));
            threads.add(thread);
            thread.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        System.out.println("Expected: 50, Actual: " + urls.size());
    }
}
