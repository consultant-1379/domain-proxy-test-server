package actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import messages.PlayMovieMessage;

public class MoviePlayerSystem extends AbstractLoggingActor {
	
	public static Props props() {
        return Props.create(MoviePlayerSystem.class);
    }

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(PlayMovieMessage.class, this::handlePlayMovieMessage) 
                .matchAny(m -> unhandled(m))
                .build();
	}
	
	void handlePlayMovieMessage(PlayMovieMessage m) {
		log().info("received PlayMovieMessage : title : {} : userId : {}", m.getMovieTitle(), m.getUserId());
	}

}
