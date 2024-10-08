package sample;
import akka.actor.AbstractLoggingActor;
import akka.actor.Props;

public class NonTrustWorthyChild extends AbstractLoggingActor {
	static class Command {
    }

    private long messages = 0L;

    private void onCommand(Command c) {
        messages++;
        if (messages % 4 == 0) {
            throw new RuntimeException("Oh no, I got four commands, I can't handle any more");
        } else {
            log().info("Got a command " + messages);
        }
    }

    public static Props props() {
        return Props.create(NonTrustWorthyChild.class);
    }

	@Override
	public Receive createReceive() {
		return receiveBuilder()
                .match(Command.class, this::onCommand)
                .build();
	}
}
