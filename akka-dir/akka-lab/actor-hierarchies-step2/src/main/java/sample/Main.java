package sample;

import java.util.Scanner;

import actors.MoviePlayerSystem;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import messages.PlayMovieMessage;

public class Main {
	
	 public static void main(final String args[]) throws Exception {
	        
	        final ActorSystem system = ActorSystem.create("movie-player-actor-system");
	        
	        
			ActorRef playbackActorRef = system.actorOf(MoviePlayerSystem.props(),"moviePlayerActor");
			playbackActorRef.tell(new PlayMovieMessage("Akka: video", 42), playbackActorRef);
			playbackActorRef.tell(new PlayMovieMessage("Vertex: video", 1), playbackActorRef);
			
			final Scanner scanner = new Scanner(System.in);
	        while (true) {
	        	System.out.println("movie-player-actor-system initialized, press q to exit");
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
