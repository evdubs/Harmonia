Harmonia is a Java console application to allow participants in the USD Swap offer market to automatically submit swap offers at competitive rates. Harmonia also estimates and displays accrued interest throughout the bot's execution. 

## Installation
Harmonia requires a JDK and Apache Maven.

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
2. If a flash return rate is the best 10 minute old (or older) offer (lowest percentage), join the other offers
3. If a fixed rate is the best offer, join the best fixed rate that is higher than the best fixed bid and is at least 10 minutes old

Harmonia recently instituted the requirement to only pay attention to offers that are at least 10 minutes old. This is a hacky way to not automatically join offers that are outlandishly far away from the core of the competitive offers. A better mechanism would likely incorporate a moving average of recent swap matches, but there is not currently a way to receive this stream from XChange. 

## License
Public domain

## Tipjar
If you like this bot and would like for development to continue, please consider sending a donation to 1JSvYwhAWMCKB4zgvMFLDzhRp8u9Hv5Fat
