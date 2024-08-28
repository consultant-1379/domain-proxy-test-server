## Demo of a Akka Actor

The `HelloWorld` actor is the application’s “main” class; when it terminates the application will shut down. 

The main business logic happens in the `preStart` method, where a `Greeter` actor is created and instructed to issue that greeting we crave for. 
When the greeter is done it will tell us so by sending back a message, and when that message has been received it will be passed into the behavior described by the `onReceive` method where we can conclude the demonstration by stopping the `HelloWorld` actor.

## The Greeter

You will be very curious to see how the `Greeter` actor performs the actual task. Open [Greeter.java](src/main/java/sample/hello/Greeter.java).

This is extremely simple now: after its creation this actor will not do anything until someone sends it a message, and if that happens to be an invitation to greet the world then the `Greeter` complies and informs the requester that the work has been done.

## Main class

Start the application main class `sbt "runMain sample.hello.Main"`. In the log output you can see the "Hello World!" greeting.

[Main2.java](src/main/java/sample/hello/Main2.java) 
This main method will then create the infrastructure (Actor System) needed for running the actors, start the given main actor and arrange for the whole application to shut down once the main actor terminates.



## Run 
java -jar target/akka-sample-main-java-0.0.1-SNAPSHOT-allinone.jar

