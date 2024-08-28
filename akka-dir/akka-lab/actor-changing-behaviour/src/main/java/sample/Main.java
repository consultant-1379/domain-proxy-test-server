
package sample;

import java.util.Scanner;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class Main {
	public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("actor-system");

        final ActorRef switchRef = system.actorOf(Switch.props(), "switch");
        switchRef.tell(new Messages.Enable(), ActorRef.noSender());
        switchRef.tell(new Messages.Enable(), ActorRef.noSender());
        
        switchRef.tell(new Messages.Disable(), ActorRef.noSender());
        switchRef.tell(new Messages.Enable(), ActorRef.noSender());
        
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
