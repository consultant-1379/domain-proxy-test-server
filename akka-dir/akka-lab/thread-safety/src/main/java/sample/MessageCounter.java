package sample;
import akka.actor.*;

public class MessageCounter extends AbstractLoggingActor {

	private int counter = 0;
	
	public static Props props() {
	      return Props.create(MessageCounter.class);
	}
	
	@Override
	public Receive createReceive() {
		return receiveBuilder()
			      .match(Message.class, this::onMessage)
			      .matchAny(o -> log().info("received unknown message: {}", o))
			      .build();
	}
	
	private void onMessage(Message message) {
	      counter++;
	      log().info("Increased counter " + counter);
	}

}
