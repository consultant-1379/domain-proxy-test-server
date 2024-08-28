package sample;

import akka.actor.*;
import java.util.Scanner;

public class Main {
	public static void main(String[] args) {
	    ActorSystem system = ActorSystem.create("MessageCountingSystem");

	    final ActorRef counter = system.actorOf(MessageCounter.props(), "msg-counter");

	    for (int i = 0; i < 5; i++) {
	      new Thread(new Runnable() {
	        @Override
	        public void run() {
	          for (int j = 0; j < 5; j++) {
	            counter.tell(new Message(), ActorRef.noSender());
	          }
	        }
	      }).start();
	    }

	    new StringBuilder("hi").reverse();

	    System.out.println("ENTER to terminate");
	    readInput(system);
	  }
	
	private static void readInput(ActorSystem system) {
		final Scanner scanner = new Scanner(System.in);
        while (true) {
        	System.out.println("actor-system initialized, press q to exit");
            final String command = scanner.nextLine();

            switch (command) {
                case "q": {
                	scanner.close();
                    system.terminate();
                    System.out.println("actor system shutdown");
                    System.exit(0);
                }
                    break;
                default:
                    System.out.println("press q to quit");
            }
        }
	}

}
