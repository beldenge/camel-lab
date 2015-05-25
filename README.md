# camel-lab
Base lab project for Apache Camel tech talk

##Prerequisites##
* JDK 1.6+
* Maven
* Your favorite flavor of Eclipse
* Git client

##Lab Scenario
Pour Decisions Homebrewing, a new homebrewing supplies retailer, needs our expert skills to integrate their enrollment process for their new rewards program.

###Requirements
1. Process enrollment files from a directory, send them to a JMS endpoint
2. Filter out files that should not be processed
3. Unmarshal the enrollment data to Java objects for processing, and then marshal them into JSON
4. The records in the file should be split so they can be processed independently
5. Implement a content-based router to perform different tasks depending on the message body
6. Invoke a "lookup" service (implemented as a bean)
7. Add error handling and retry logic
8. **Extra credit:** test the route with a JUnit test

##Lab 1
Import existing maven project
Group: com.redhat.techtalks
Artifact: camel-lab
Version: 0.0.1-SNAPSHOT
Packaging: jar

Add dependencies to pom (camel-core, camel-jms, activemq-all)
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
	
Create class EnrollmentRouteBuilder
Package: com.redhat.techtalks.camel.routes
Extends: RouteBuilder
		from("file:input?noop=true")
			.to("jms:queue:enrollees");

		from("jms:queue:enrollees")
			.to("log:com.redhat.techtalks.camel?level=INFO&showAll=true");

Examine input directory
	-3 input files
	
Configure queue manager and main class
Create class EnrollmentApplication
Package: com.redhat.techtalks.camel
	public static void main(String[] args) throws Exception {
		ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");

		CamelContext camelContext = new DefaultCamelContext();

		camelContext.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

		camelContext.addRoutes(new EnrollmentRouteBuilder());

		camelContext.start();
		Thread.sleep(5000);
		camelContext.stop();
	}

Right click EnrollmentApplication.java, Run As --> Java Application
Examine logs
	
##Lab 2
Add before producer endpoint
.filter(header("CamelFileNameOnly").startsWith("rewards"))
Examine log4j.properties


Antoher way you could do this in our specific scenario would be to do the following, but we decided to go with the message filter EIP since it is more flexible when working with different types of endpoints.
	from("file:input?noop=true&antInclude=rewards*.txt")

Right click EnrollmentApplication.java, Run As --> Java Application
Examine logs
	
##Lab 3
Add before route
	DataFormat bindy = new BindyCsvDataFormat("com.redhat.techtalks.camel.model");
Add after filer processor
	.unmarshal(bindy)

Seeing workspace errors?  Add camel-bindy dependency:

Examine rewards*.txt files

Update Enrollment.java

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

Right click EnrollmentApplication.java, Run As --> Java Application
Examine logs

Seeing serialization errors?  We could fix this by making the Enrollment class implement serializable.  Instead, let's convert it to Json.

Add after the unmarshal processor:
.marshal().json(JsonLibrary.Jackson)

Remember to include the Jackson dependency.

		<dependency>
			<groupId>org.apache.camel</groupId>
			<artifactId>camel-jackson</artifactId>
			<version>${camel.version}</version>
		</dependency>
		
Right click EnrollmentApplication.java, Run As --> Java Application
Examine logs, it should run without errors this time.  Also check out the message body.

##Lab 4
The problem is that our json message is pretty unwieldy, as it is a List of HashMaps keyed by the record type.

Add a splitter before we do the unmarshalling:
.split(body())

Right click EnrollmentApplication.java, Run As --> Java Application
Examine logs.

It's four separate message now, but they are still represented as HashMaps.  This is due to the way that Bindy unmarshals data, as it could potentially unmarshal a single record into multiple classes.

After our splitter, add the following:
.setBody(simple("${body[com.redhat.techtalks.camel.model.Enrollment]}"))

Right click EnrollmentApplication.java, Run As --> Java Application
Examine logs.  Now we have just the json representation of the Enrollment class in the message body.

Add a content-based router just before the marshal to JSON.
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
			
Right click EnrollmentApplication.java, Run As --> Java Application
Examine logs.  You should see the greetings displayed as expected.

##Lab 5
Add a bean processor to "enrich" the message with the age calculated from the enrollee's date of birth.  Add this just after the consumer on the second route.
			.unmarshal().json(JsonLibrary.Jackson, Enrollment.class)
			.bean(new AgeCalculator(), "calculateAge(${body.dateOfBirth})")
			
Right click EnrollmentApplication.java, Run As --> Java Application
Examine logs.

The first message failed.  Quickly examine the AgeCalculator class to understand why.

Add retry logic:
Immediately after the consumer on the second route, add an error handler definition:
			.onException(IOException.class)
				.maximumRedeliveries(1)
				.redeliveryDelay(500)
			.end()
			
Right click EnrollmentApplication.java, Run As --> Java Application
Examine logs.  We still see part of a stacktrace.  Why?  Can we confirm that the message was successfully retried?


##Extra Credit
Add a test-scoped dependency:
		<dependency>
			<groupId>org.apache.camel</groupId>
			<artifactId>camel-test</artifactId>
			<version>${camel.version}</version>
			<scope>test</scope>
		</dependency>

Create class EnrollmentRouteBuilderTest
Package: com.redhat.techtalks.camel
Extends: CamelTestSupport

Add the following in the createCamelContext() method:
		ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");

		CamelContext camelContext = super.createCamelContext();

		camelContext.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

		return camelContext;
		
Add the following in the createRouteBuilder() method:
		return new EnrollmentRouteBuilder();
		
Add a junit test:
	@Test
	public void testEnrolleesConsumer() throws InterruptedException
	{
		MockEndpoint mock = getMockEndpoint("mock:testQueue");
		mock.setAssertPeriod(5000);  // Required in order to expect exactly 4 messages
		mock.expectedMessageCount(4);
		mock.expectedBodiesReceivedInAnyOrder("57", "40", "5", "31");
		mock.assertIsSatisfied();
	}
	
Wait, what is sending to this mock queue?
Unfortunately, we have to augment our route with this (add at very end of second route):
.to("mock:testQueue")

Right click EnrollmentRouteBuilder.java, Run As --> JUnit Test
