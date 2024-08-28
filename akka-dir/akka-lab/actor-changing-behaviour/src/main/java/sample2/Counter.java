package sample2;

import akka.actor.AbstractActor;
import akka.actor.AbstractLoggingActor;

public class Counter extends AbstractLoggingActor {

	private AbstractActor.Receive counter;
	
	private int n;
	
	public Counter() {
		counter = receiveBuilder()
				.matchEquals("incr", s -> {n++; getContext().become(counter);})
				.matchEquals("get", (s) -> System.out.println(n))
				.build();
		
		getContext().become(counter);
	}
	
	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.matchAny(s->getContext().become(counter))
				.build();
	}

}
