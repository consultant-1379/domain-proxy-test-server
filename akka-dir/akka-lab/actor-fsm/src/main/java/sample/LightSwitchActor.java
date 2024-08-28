package sample;

import akka.actor.*;

import static sample.Messages.PowerOn;
import static sample.Messages.PowerOff;

public class LightSwitchActor extends AbstractLoggingFSM<LightSwitchState, LightSwitchData> {
	
	public LightSwitchActor() {
		startWith(LightSwitchState.Off, new NoData());
		
		when(LightSwitchState.Off,
				matchEvent(PowerOn.class, (event, data) ->
				goTo(LightSwitchState.On).using(data)));
		
		when(LightSwitchState.On,
				matchEvent(PowerOff.class, (event, data) ->
				goTo(LightSwitchState.Off).using(data)));
		
	  onTransition (
		  matchState(LightSwitchState.Off, LightSwitchState.On, () -> System.out.println("Moved from Off to On"))
		  .state(LightSwitchState.On, LightSwitchState.Off, () -> System.out.println("Moved from On to Off"))
	  );
		
		initialize();
	}
}
