Introduction to: Reactive System
================================

A reactive system is a server which exposes services using message exchange pattern rather than synchronos IO communications (for example HTTP).
Using message exchange rather than synchronous communication helps to build more decoupled systems, which gives us several advatages like:

1. Isolation: if a reactive system fails it doesn't affect its client.
2. Location transparency: reactive systems they don't communicate directly but through messages sent to 
   an address (topic), that way is not necessary to know the location of the reactive system exposing the
   service as long we can send a message to the topic address.
3. Elasticity: reactive services can implement scaling strategy on the way they consume the messages from a
   topic allowing to add (scale out) and remove (scale down) more reactive systems depending on the current
   demand.
4. DOS Immune: ...


Reactive System
---------------

In order to build a reactive system you need 3 elements:

1. A source of ReactiveRequest messages
2. A router able to dispatch ReactiveRequest messages to the associated ReactiveService
3. A sink of ReactiveResponse messages which will send the response to the right destination

```
    _____________________________________________
   |               REACTIVE SYSTEM               |
   |                                             |
   |      ________      _______      ______      |
   |     |        |    |       |    |      |     |
   |     | Source | ~> | Route | ~> | Sink |     |
   |     |________|    |_______|    |______|     |
   |                                             |
   |_____________________________________________|
   
```

*Scala API:*

```scala

import org.patricknoir.kafka.reactive.server.dsl._

implicit val system: ActorSystem = ...
import system.dispatcher

val source: Source[KafkaRequestEnvelope, _] = ...

val route: ReactiveRoute = ...

val sink: Sink[Future[kafkaResponseEnvelope], _] = ...

val reactiveSys: ReactiveSystem = source ~> route ~> sink
```

Or alternatively the DSL exposes also:

```scala

val reactiveSys: ReactiveSystem = source via route to sink
```

Or more basic:

```scala

val reactiveSystem: ReactiveSystem = ReactiveSystem(source, route, sink)
```


Reactive Source
---------------

A reactive source is the inbound gateway from which request messages are processed by the reactive system.
As part of the Kafka-Reactive-System there is a KafkaReactiveSource object which provides a method create to build
a reactive source able to consume from a Kafka topic.

```
   _________________       ______________________________________ . _ . _ _ 
  |      KAFKA      |     |                                 REACTIVE SYSTEM 
  |     _______     |     |     ________________________________     
  |    | topic |~~~~~~~~~~~~~~>|  Source[KafkaRequestEnvelope]  |
  |    '-------'    |     |    |________________________________|
  |_________________|     |_______________________________________ . _ . _ .
  

```

*Scala API:*

```scala

val source: Source[KafkaRequestEnvelope, _] = ReactiveKafkaSource.create(
  requestTopic = "simpleService", 
  bootstrapServers = Set("localhost:9092"), 
  clientId = "simpleServiceClient1"
)

```

Reactive Route
--------------

The reactive route is the component in charge to analise the KafkaRequestEnvelope message and dispatch the request to the relevant service.
In order to accomplish to its task the reactive route must know all the reactive services we want to expose.

A reactive route can be though as a mapping between the Reactive Service URI and its current implementation:

``` 
                                    
                                        /---->{Service: echo}
                                        |                    
             __________                 |                    
            |          |----------------'                    
            | Reactive |                                     
            |  Route   |------------------------>{Service: size}
            |          |                                        
            '----------'                                        
                    |                                           
                    \--------------->{Service: reverse}         
                                   
                                      
```

### Reactive Service

The reactive service represents the entry point of your business logic. A reactive service is nothing more than a function which has an input 
and can produce a result, because the input and output are delivered through messages the input type must be deserializable and the output serializable.

*Scala API:*

```scala

case class ReactiveService[-In: ReactiveDeserializer, +Out: ReactiveSerializer](id: String)(f: In => Future[Error Xor Out])

```

From the Scala API you can notice that the service has a unique ID, the execution of the ReactiveService is "intrinsicly" asynchronous and eventually returns a business error.  

### Reactive Route DSL

In order to make it easy to create a ReactiveRoute with all its services associated a specific dsl has been developed.

*Scala API:*

```scala

val route: ReactiveRoute = request.aSync[String, String]("echo") { in => 
    s"echoing: $in"
  } ~ request.aSync("size") { (in: String) =>
    in.length
  } ~ request.sync("reverse") { (in: String) =>
    in.reverse
  }

```

### Route DSL: The request object

...

#### sync vs aSync

...

#### Route DSL: Future Flatten

...

#### Route DSL: Handle Xor

...

Reactive Sink
-------------

...

Reactive Client
---------------

...


```scala

trait ReactiveClient {

  def request[In: ReactiveSerializer, Out: ReactiveDeserializer]
    (destination: String, payload: In)(implicit timeout: Timeout): Future[Error Xor Out]

}
```


*Kafka Implementation:*

```scala
class KafkaReactiveClient(settings: KafkaRClientSettings)(implicit system: ActorSystem) extends ReactiveClient
```


*Scala API:*

```scala

implicit system: ActorSystem = ...
import system.dispatcher
implicit val timeout = Timeout(3 seconds)
val client = new KafkaReactiveClient(KafkaRClientSettings.default)
val fResponse = client.request[String, String]("kafka:echoInbound/echo", "patrick")

result.onSuccess { 
    case Xor.Right(result: String) = println(result)
}

...

```

### Reactive Serializer/Deserializer

...

```scala
package object common {

  object deserializer {
    def deserialize[Out: ReactiveDeserializer](in: String): Xor[Error, Out] = implicitly[ReactiveDeserializer[Out]].deserialize(in.getBytes)
  }

  object serializer {
    def serialize[In: ReactiveSerializer](in: In): String = new String(implicitly[ReactiveSerializer[In]].serialize(in))
  }

}
```

#### Default Serializers

```scala
trait ReactiveSerializer[Payload] {

  def serialize(payload: Payload): Array[Byte]

}

object ReactiveSerializer {
  implicit val stringSerializer = new ReactiveSerializer[String] {
    override def serialize(payload: String) = payload.getBytes
  }

  implicit def circeEncoderSerializer[In: Encoder] = new ReactiveSerializer[In] {
    override def serialize(payload: In) = payload.asJson.noSpaces.getBytes
  }

  implicit val byteArraySerializer = new ReactiveSerializer[Array[Byte]] {
    override def serialize(payload: Array[Byte]) = payload
  }
}
```

#### Default Deserializers

```scala
trait ReactiveDeserializer[Payload] {

  def deserialize(input: Array[Byte]): Xor[Error, Payload]

}

object ReactiveDeserializer {
  implicit val stringDeserializer = new ReactiveDeserializer[String] {
    override def deserialize(input: Array[Byte]) = Xor.Right(new String(input))
  }

  implicit def circeDecoderDeserializer[Out: Decoder] = new ReactiveDeserializer[Out] {
    override def deserialize(input: Array[Byte]) = decode[Out](new String(input)).leftMap(err => new Error(err)) //FIXME : use custom errors
  }

  implicit val byteArrayDeserializer = new ReactiveDeserializer[Array[Byte]] {
    override def deserialize(input: Array[Byte]) = Xor.Right(input)
  }
}
```

### Actor Per Request - Correlation Header

...