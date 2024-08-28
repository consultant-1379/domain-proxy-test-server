package actors;

import akka.actor.AbstractLoggingActor;
import messages.PlayMovieMessage;

public class UserActor extends AbstractLoggingActor {
	
	private int userId;
	
	public UserActor(int id) {
        userId = id;
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
