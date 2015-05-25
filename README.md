# camel-lab
This is the base lab project for the Apache Camel tech talk by George Belden.

The completed solution is available at [https://github.com/beldenge/camel-lab-solution](https://github.com/beldenge/camel-lab-solution).

##Prerequisites##
* JDK 1.6+
* Maven
* Your favorite flavor of Eclipse
* Git client

##Lab Scenario
Pour Decisions Homebrewing, a homebrewing supplies retailer, is implementing a new rewards program for their customers which requires our expert skills to integrate the enrollment process with this new program.  We will strive to show them the power and brevity of Apache Camel's Java DSL.

##Requirements
1. Process enrollment files from a directory, send them to a JMS endpoint
2. Filter out files that should not be processed
3. Unmarshal the enrollment data to Java objects for process-ing, and then marshal them into JSON
4. The records in the file should be split so they can be processed independently
5. Implement a content-based router to perform different tasks depending on the message body
6. Invoke a "lookup" service (implemented as a bean)
7. Add error handling and retry logic
8. **Extra credit:** test the route with a JUnit test

##Lab 1
Begin by cloning this project and importing it into Eclipse as an existing maven project.  We will build onto it in the following labs.

To start, we will need a few initial dependencies in our pom.  The camel.version property should already be defined.
```XML
		<dependency>
			<groupId>org.apache.camel</groupId>
			<artifactId>camel-core</artifactId>
			<version>${camel.version}</version>
		</dependency>
		<dependency>
			<groupId>org.apache.camel</groupId>
			<artifactId>camel-jms</artifactId>
			<version>${camel.version}</version>
		</dependency>
		<dependency>
			<groupId>org.apache.activemq</groupId>
			<artifactId>activemq-all</artifactId>
			<version>${activemq.version}</version>
		</dependency>
```
	
Create class EnrollmentRouteBuilder  
Package: com.redhat.techtalks.camel.routes  
Superclass: org.apache.camel.builder.RouteBuilder

Override the configure() method, and inside here we will add a couple of very simple routes.  This will poll a directory for new files and send them to a JMS endpoint.  Since this is a new endpoint, we define the second route to just read from that endpoint and log the payload so the messages don't sit on the queue indefinitely.
```Java
		from("file:input?noop=true")
			.to("jms:queue:enrollees");

		from("jms:queue:enrollees")
			.to("log:com.redhat.techtalks.camel?level=INFO&showAll=true");
```

Examine input directory
* Here we have 3 input files, only 2 of which are related to rewards enrollment

Examine log4j.properties
* Camel integrates with log4j by default

Create class EnrollmentApplication  
Package: com.redhat.techtalks.camel  

Configure queue manager and main method.
```Java
	public static void main(String[] args) throws Exception {
		ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");

		CamelContext camelContext = new DefaultCamelContext();

		camelContext.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

		camelContext.addRoutes(new EnrollmentRouteBuilder());

		camelContext.start();
		Thread.sleep(5000);
		camelContext.stop();
	}
```
Right click EnrollmentApplication.java, `Run As --> Java Application`  
Examine the logs to see that the contents of each file are logged by the second route.
	
##Lab 2
In the first route, add the following before the producer endpoint:
```Java
.filter(header("CamelFileNameOnly").startsWith("rewards"))
```

Anoher way you could do this in our specific scenario would be to do the following, but we decided to go with the message filter EIP since it is more flexible when working with different types of endpoints.
```Java
	from("file:input?noop=true&antInclude=rewards*.txt")
```
Right click EnrollmentApplication.java, `Run As --> Java Application`  
Examine the logs to see that only the rewards enrollment files are processed.
	
##Lab 3
Add the following at the top of EnrollmentRouteBuilder:
```Java
	DataFormat bindy = new BindyCsvDataFormat("com.redhat.techtalks.camel.model");
```
Immediately after the filter processor, add the following:
```Java
	.unmarshal(bindy)
```
Seeing workspace errors?  We need to add the camel-bindy dependency:
```XML
		<dependency>
			<groupId>org.apache.camel</groupId>
			<artifactId>camel-bindy</artifactId>
			<version>${camel.version}</version>
		</dependency>
```

Examine rewards*.txt files
* Notice the file contains last name, first name, date of birth, and language

Update Enrollment.java so Camel will know how to parse this file:
```Java
@CsvRecord(separator = "\\|", crlf = "WINDOWS")
public class Enrollment {
    @DataField(pos = 1)
    private String lastName;

    @DataField(pos = 2)
    private String firstName;

    @DataField(pos = 3, pattern = "yyyy-MM-dd")
    private Date dateOfBirth;

    @DataField(pos = 4)
    private String language;
```
Right click EnrollmentApplication.java, `Run As --> Java Application`  

Seeing serialization errors?  We could fix this by making the Enrollment class implement serializable.  Instead, let's convert it to JSON.

Add after the unmarshal processor:
```Java
.marshal().json(JsonLibrary.Jackson)
```
Be sure to include the Jackson dependency:
```XML
		<dependency>
			<groupId>org.apache.camel</groupId>
			<artifactId>camel-jackson</artifactId>
			<version>${camel.version}</version>
		</dependency>
```		
Right click EnrollmentApplication.java, `Run As --> Java Application`  
Examine the logs.  This time it should run without errors.  Also check out the message body to see that it is formatted as JSON.

##Lab 4
The problem is that our data structure is pretty unwieldy, as Bindy structures it as a List of HashMaps keyed by the record type.

Add a splitter between the unmarshal and marshal steps:
```Java
.split(body())
```
Right click EnrollmentApplication.java, `Run As --> Java Application`  
Examine the logs.

It's four separate message now, but they are still represented as HashMaps.  This is due to the way that Bindy unmarshals data, as it could potentially unmarshal a single record into multiple classes.

After our splitter, add the following:
```Java
.setBody(simple("${body[com.redhat.techtalks.camel.model.Enrollment]}"))
```
Right click EnrollmentApplication.java, `Run As --> Java Application`  
Examine the logs.  Now we have just the JSON representation of the Enrollment class in the message body.

##Lab 5
Add a content-based router just before we marshal to JSON.
```Java
			.choice()
				.when(simple("${body.language} == 'ES'"))
					.log("Hola ${body.firstName}!")
					.endChoice()
				.when(simple("${body.language} == 'EN'"))
					.log("Hello ${body.firstName}!")
					.endChoice()
				.otherwise()
					.log(LoggingLevel.ERROR, "OMG!")
					.endChoice()
			.end()
```			
Right click EnrollmentApplication.java, `Run As --> Java Application`  
Examine the logs.  You should see the greetings displayed as expected.

##Lab 6
Add a bean processor to "enrich" the message with the age calculated from the enrollee's date of birth.  Add this just after the consumer on the second route.
```Java
			.unmarshal().json(JsonLibrary.Jackson, Enrollment.class)
			.bean(new AgeCalculator(), "calculateAge(${body.dateOfBirth})")
```			
Right click EnrollmentApplication.java, `Run As --> Java Application`  
Examine the logs.

The first message failed.  Quickly examine the AgeCalculator class to understand why.

##Lab 7
We need to add some retry logic.  
Immediately after the consumer on the second route, add an error handler definition:
```Java
			.onException(IOException.class)
				.maximumRedeliveries(1)
				.redeliveryDelay(500)
			.end()
```			
Right click EnrollmentApplication.java, `Run As --> Java Application`  
Examine the logs.  We still see part of a stacktrace.  Why?  Can we confirm that the message was successfully retried?

##Extra Credit
Add a test-scoped dependency:
```XML
		<dependency>
			<groupId>org.apache.camel</groupId>
			<artifactId>camel-test</artifactId>
			<version>${camel.version}</version>
			<scope>test</scope>
		</dependency>
```
Create class EnrollmentRouteBuilderTest  
Package: com.redhat.techtalks.camel  
Extends: org.apache.camel.test.junit4.CamelTestSupport

Override the createCamelContext() method and add the following in the method body:
```Java
		ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");

		CamelContext camelContext = super.createCamelContext();

		camelContext.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

		return camelContext;
```		
Override the createRouteBuilder() method and add the following in method body:
```Java
		return new EnrollmentRouteBuilder();
```		
Add a junit test:
```Java
	@Test
	public void testEnrolleesConsumer() throws InterruptedException
	{
		MockEndpoint mock = getMockEndpoint("mock:testQueue");
		mock.setAssertPeriod(5000);  // Required in order to expect exactly 4 messages
		mock.expectedMessageCount(4);
		mock.expectedBodiesReceivedInAnyOrder("57", "40", "5", "31");
		mock.assertIsSatisfied();
	}
```	
Wait, what is sending to this mock queue?  
Unfortunately, we have to augment our original route with this (add at very end of the second route):
```Java
.to("mock:testQueue")
```
Right click EnrollmentRouteBuilder.java, `Run As --> JUnit Test`  
Examine the logs.  The test should succeed.  If not, take a look at the [solution project](https://github.com/beldenge/camel-lab-solution).

##Best Practices
Here we spun up our CamelContext manually and let it die after a static amount of time.  This is very inflexible and obviously not a best practice for Camel applications.

One other option for standalone applications is to instantiate a 
[Main class](https://camel.apache.org/running-camel-standalone-and-have-it-keep-running.html).

We can also externalize our endpoint URIs to make our code more readable and enable us to change endpoint options without rebuilding our entire application.  Check out the "Bridging Spring and Camel property placeholders" section in the [Using PropertyPlaceholder](https://camel.apache.org/using-propertyplaceholder.html) page.
