Harmonia is a Java console application to allow participants in the USD margin funding market to automatically submit margin funding offers at competitive rates. Harmonia also estimates and displays accrued interest throughout the bot's execution. 

## Installation
Harmonia requires a JDK and Apache Maven.

## Starting Harmonia
After cloning the repository, execute the following commands in the Harmonia base directory:

	mvn compile

	mvn exec:java -Dexec.mainClass="name.evdubs.harmonia.Harmonia"

This process should build, download dependencies, and run Harmonia

## Usage
When Harmonia starts, you are prompted to enter your API Key and Secret Key. There are perhaps other, better methods of acquiring and using these details. Please note that anyone with access to the system you execute Harmonia on can dump the heap and find your keys. Therefore, it is wise to run Harmonia in a jailed environment that only you have access to.

## Operation
Harmonia is a simple margin funding offer bot. It will attempt to offer margin funds using the following logic:

1. If a flash return rate is bid, hit that bid. Here, the market (everyone else's orders) may look like: USD margin funding demand: 1 order for 30 days flash return rate per day for $10,000. Harmonia will see this order and take it. As a result, you will have outstanding margin funding provided at FRR for 30 days.
2. If a flash return rate is the best 10 minute old (or older) offer (lowest percentage), join the other offers. Here, there are only fixed rate demands, e.g. USD margin funding demand: 1 order for 30 days 0.05% per day for $10,000. On the offer side, the flash return rate will be lower than other offers, e.g. USD margin funding demand: 1 offer for 30 days FRR (0.07%) for $10,000 and 1 offer for 30 days 0.09% for $10,000. Harmonia will see the FRR order and post an order with your remaining deposit balance for 30 days FRR.
3. If a fixed rate is the best offer, join the best fixed rate that is higher than the best fixed bid and is at least 10 minutes old. Here, the demands may look something like: 1 order for 30 days 0.07% per day for $10,000. The offers may look like: 1 offer for 30 days 0.065% per day for $100; 1 offer for 30 days 0.08% per day $500. Harmonia will send an order at 0.08% as the other offer is lower than the highest demand rate.

Harmonia recently instituted the requirement to only pay attention to offers that are at least 10 minutes old. This is a hacky way to not automatically join offers that are outlandishly far away from the core of the competitive offers. A better mechanism would likely incorporate a moving average of recent margin funding matches, but there is not currently a way to receive this stream from XChange. 

## License
Public domain

## Tipjar
If you like this bot and would like for development to continue, please consider sending a donation to 1JSvYwhAWMCKB4zgvMFLDzhRp8u9Hv5Fat
