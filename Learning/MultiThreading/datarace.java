package Learning.MultiThreading;

public class datarace {
    private static int result = 0;

    public static void main(String[] args) {
        Thread worker = new Thread(() -> {
            result = 5005; // Line A
        });

        worker.start(); 

        // CRITICAL ERROR: .join() is removed!
        // try {
        //     worker.join();
        // } catch (InterruptedException e) {
            
        // }

        // Line B: Will this print 0 or 5005?
        System.out.println("Result is: " + result); 
    }
}
