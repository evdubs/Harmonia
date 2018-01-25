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

#### If a flash return rate is bid, hit that bid
Here, the market (everyone else's orders) may look like

| Bid duration | Bid amount | Bid rate    | Offer rate  | Offer amount | Offer duration |
|:-------------|-----------:|------------:|------------:|-------------:|:---------------|
|2-30 days     | 20,000     | 0.05% (FRR) | 0.06%       | 30,000       | 30 days        |

Harmonia will see this bid and hit it. As a result, you will have outstanding margin funding provided at FRR for 30 days.

#### If a flash return rate is the best 10 minute old (or older) offer (lowest percentage), join the other offers
Here, the market may look like

| Bid duration | Bid amount | Bid rate    | Offer rate  | Offer amount | Offer duration |
|:-------------|-----------:|------------:|------------:|-------------:|:---------------|
|2-30 days     | 20,000     | 0.05%       | 0.06% (FRR) | 30,000       | 30 days        |

Harmonia will see the FRR order and send an offer with your remaining deposit balance for 30 days FRR. This offer will sit on the book and wait for a margin lender to take it. This calculation is computed once every twenty seconds, so if the market moves, Harmonia will change the offer twenty seconds later.

#### If a fixed rate is the best offer, join the best fixed rate that is higher than the best fixed bid and is at least 10 minutes old 
Here, the market may look like

| Bid duration | Bid amount | Bid rate    | Offer rate  | Offer amount | Offer duration |
|:-------------|-----------:|------------:|------------:|-------------:|:---------------|
|2-20 days     | 20,000     | 0.06%       | 0.055%      | 30,000       | 30 days        |
|2-30 days     | 40,000     | 0.05%       | 0.065%      | 60,000       | 30 days        |

Harmonia will send an offer at 0.065% as the 0.055% offer is lower than the highest demand rate of 0.06%. This offer will sit on the book and wait for a margin lender to take it. This calculation is computed once every twenty seconds, so if the market moves, Harmonia will change the offer twenty seconds later.

Harmonia recently instituted the requirement to only pay attention to offers that are at least 10 minutes old. This is a hacky way to not automatically join offers that are outlandishly far away from the core of the competitive offers. A better mechanism would likely incorporate a moving average of recent margin funding matches, but there is not currently a way to receive this stream from XChange. 

## License
Public domain

## Tipjar
If you like this bot and would like for development to continue, please consider sending a donation to 1JSvYwhAWMCKB4zgvMFLDzhRp8u9Hv5Fat
