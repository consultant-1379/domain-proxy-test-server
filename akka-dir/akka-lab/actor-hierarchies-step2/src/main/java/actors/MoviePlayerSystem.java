package actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import messages.PlayMovieMessage;

public class MoviePlayerSystem extends AbstractLoggingActor {
	
	ActorRef userCoordinator;
	
	public static Props props() {
        return Props.create(MoviePlayerSystem.class);
    }
	
	public MoviePlayerSystem() {
		userCoordinator = getContext().actorOf(Props.create(UserCoordinatorActor.class), "UserCoordinatorActor");
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(PlayMovieMessage.class, this::handlePlayMovieMessage) 
                .matchAny(m -> unhandled(m))
                .build();
	}
	
	void handlePlayMovieMessage(PlayMovieMessage m) {
		userCoordinator.tell(m, self());
	}

}
