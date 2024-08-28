package sample;

import static sample.Messages.Disable;
import static sample.Messages.Enable;

import akka.actor.AbstractActor;
import akka.actor.AbstractLoggingActor;
import akka.actor.Props;

public class Switch extends AbstractLoggingActor {

	
    private AbstractActor.Receive enabled;
    private AbstractActor.Receive disabled;
    
    
    public Switch() {
    	enabled = receiveBuilder()
                .match(Disable.class, this::onDisable)
                .matchAny(s -> {
                    log().info("I am already enabled?");
                  })
                .build();

        disabled = receiveBuilder()
                .match(Enable.class, this::onEnable)
                .matchAny(s -> {
                	log().info("I am already disabled?");
                    
                  })
                .build();
        
        getContext().become(disabled);
    }
    
    private void onEnable(Enable enable) {
    	 log().info("I am enabled");
    	 getContext().become(enabled);
    	
    }
    
    private void onDisable(Disable disable) {
    	log().info("I am disabled");
    	getContext().become(disabled);
    }
	public static Props props() {
        return Props.create(Switch.class);
    }
	
	@Override
	public Receive createReceive() {
		 return receiveBuilder()
 		        .match(Disable.class, s -> getContext().become(disabled))
 		        .match(Enable.class, s -> getContext().become(enabled))
 		        .build();
	}

}
