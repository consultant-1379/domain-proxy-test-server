package sample;

import java.util.Scanner;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class Main {
	public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("actor-system");

        final ActorRef supervisor = system.actorOf(Supervisor.props(), "supervisor");

        for (int i = 0; i < 50; i++) {
            supervisor.tell(new NonTrustWorthyChild.Command(), ActorRef.noSender());
        }
        
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
