package Learning.MultiThreading;
public class Main {
    public static void main(String[] args) {
        // Initializing 2 Threads
        MultiThread thing = new MultiThread();
        thing.start();

        MultiThread thing2 = new MultiThread();
        thing2.start();

    }
}
