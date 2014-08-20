Harmonia is a Java console application to allow participants in the USD Swap offer market to automatically submit swap offers at competitive rates.

## Installation
Harmonia requires a JDK and Apache Maven

## Starting Harmonia
After cloning the repository, execute the following commands in the Harmonia base directory:

	mvn compile

	mvn exec:java -Dexec.mainClass="name.evdubs.harmonia.Harmonia"

This process should build, download dependencies, and run Harmonia

## Usage
When Harmonia starts, you are prompted to enter your API Key and Secret Key. There are perhaps other, better methods of acquiring and using these details. Please note that anyone with access to the system you execute Harmonia on can dump the heap and find your keys.

## Operation
Harmonia is a simple swap offer bot. It will attempt to offer swaps using the following logic:

1. If a flash return rate is bid, hit that bid
2. If a flash return rate is the best offer (lowest percentage), join the other offers
3. If a fixed rate is the best offer, join the best fixed rate that is higher than the best fixed bid

## License
Public domain
