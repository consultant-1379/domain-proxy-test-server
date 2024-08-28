package actors;

import java.util.HashMap;
import java.util.Map;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import messages.PlayMovieMessage;

public class UserCoordinatorActor extends AbstractLoggingActor {
	private Map<Integer, ActorRef> users = new HashMap<>();

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(PlayMovieMessage.class, this::handlePlayMovieMessage) 
                .matchAny(m -> unhandled(m))
                .build();
	}
	
	void handlePlayMovieMessage(PlayMovieMessage m) {
		createChildUserIfNotExists(m.getUserId());
        ActorRef childActorRef = users.get(m.getUserId());
        childActorRef.tell(m, self());
	}

	private void createChildUserIfNotExists(int userId) {
		if (!users.containsKey(userId)) {
			final ActorRef userRef = getContext().actorOf(Props.create(UserActor.class, userId), "User" + userId);
			users.put(userId, userRef);
			log().info("UserCoordinatorActor created new child UserActor {}","User" + userId);
		}
	}
	
	
	
	
	
}
