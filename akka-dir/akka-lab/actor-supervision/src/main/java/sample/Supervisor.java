package sample;

import akka.actor.*;
import akka.japi.pf.DeciderBuilder;
import scala.concurrent.duration.Duration;
import static akka.actor.SupervisorStrategy.*;
import java.util.concurrent.TimeUnit;

public class Supervisor extends AbstractLoggingActor {

    final ActorRef child = getContext().actorOf(NonTrustWorthyChild.props(), "child");
    private int counter = 0;

    @Override
	public Receive createReceive() {
		return receiveBuilder()
                .match(NonTrustWorthyChild.Command.class, this::forward)
                .build();
	}
    
    private void forward(NonTrustWorthyChild.Command command) {
        counter++;
        log().info("forward message to child " + counter);
        child.forward(command, getContext());
    }
    /*
    The first parameter defines the maxNrOfRetries, which is
    the number of times a child actor is allowed to be restarted
    before the child actor is stopped. (A negative value indicates
    no limit).
     
    The withinTimeRange parameter defines a duration
    of the time window for maxNrOfRetries. As defined above the
    strategy tries 10 times in 10 seconds.
     
    The DeciderBuilder defines matches on occurring exceptions and how to react to them.
    In this case if there are 10 retries within 10 seconds the Supervisor stops the
    NonTrustWorthyChild and all remaining messages are sent to
    the dead letter box.
    */

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(
                10,
                Duration.create(10, TimeUnit.SECONDS),
                DeciderBuilder
                        .match(RuntimeException.class, ex -> restart())
                        .build()
        );
    }

    public static Props props() {
        return Props.create(Supervisor.class);
    }

}
