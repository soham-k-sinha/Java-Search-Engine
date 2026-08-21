package Learning.MultiThreading;
public class MultiThread extends Thread {
    
    // A thread that prints 1-5 with a 1 second gap
    @Override
    public void run() {
        for (int i = 1; i <= 5; i++) {
            System.out.println(i);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
    }
}
