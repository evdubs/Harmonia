package name.evdubs.harmonia;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.bitfinex.v1.BitfinexExchange;
import com.xeiam.xchange.bitfinex.v1.BitfinexOrderType;
import com.xeiam.xchange.bitfinex.v1.dto.account.BitfinexBalancesResponse;
import com.xeiam.xchange.bitfinex.v1.dto.marketdata.BitfinexLend;
import com.xeiam.xchange.bitfinex.v1.dto.marketdata.BitfinexLendDepth;
import com.xeiam.xchange.bitfinex.v1.dto.marketdata.BitfinexLendLevel;
import com.xeiam.xchange.bitfinex.v1.dto.marketdata.BitfinexTicker;
import com.xeiam.xchange.bitfinex.v1.dto.trade.BitfinexCreditResponse;
import com.xeiam.xchange.bitfinex.v1.dto.trade.BitfinexOfferStatusResponse;
import com.xeiam.xchange.bitfinex.v1.service.polling.BitfinexAccountServiceRaw;
import com.xeiam.xchange.bitfinex.v1.service.polling.BitfinexMarketDataServiceRaw;
import com.xeiam.xchange.bitfinex.v1.service.polling.BitfinexTradeServiceRaw;
import com.xeiam.xchange.dto.Order.OrderType;
import com.xeiam.xchange.dto.trade.FixedRateLoanOrder;
import com.xeiam.xchange.dto.trade.FloatingRateLoanOrder;

/**
 * Main entry point
 *
 */
public class Harmonia {
  public static void main(String[] args) {
    // Use the factory to get BFX exchange API using default settings
    Exchange bfx = ExchangeFactory.INSTANCE.createExchange(BitfinexExchange.class.getName());

    ExchangeSpecification bfxSpec = bfx.getDefaultExchangeSpecification();
    String apiKey = "";
    String secretKey = "";

    try {
      System.out.print("API Key: ");
      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
      apiKey = br.readLine();

      System.out.print("Secret Key: ");
      secretKey = br.readLine();

      br.close();
    } catch (IOException e2) {
      System.out.println("Could not read in keys; exiting.");
      e2.printStackTrace();
      System.exit(1);
    }

    bfxSpec.setApiKey(apiKey);
    bfxSpec.setSecretKey(secretKey);

    bfx.applySpecification(bfxSpec);

    // Get the necessary services
    BitfinexMarketDataServiceRaw marketDataService = (BitfinexMarketDataServiceRaw) bfx.getPollingMarketDataService();
    BitfinexAccountServiceRaw accountService = (BitfinexAccountServiceRaw) bfx.getPollingAccountService();
    BitfinexTradeServiceRaw tradeService = (BitfinexTradeServiceRaw) bfx.getPollingTradeService();

    
    final BigDecimal MIN_FUNDS_USD = new BigDecimal("50.0"); // minimum amount needed (USD) to lend
    final double millisecondsInDay = 86400000.0;
    
    final String[] currencyArray = {"USD", "BTC", "LTC", "TH1"};
    final BigDecimal[] maxRateArray = {new BigDecimal("2555"), new BigDecimal("2555"), new BigDecimal("2555"), new BigDecimal("2555")}; // 7% per day * 365 days
    final BigDecimal[] minRateArray = {new BigDecimal("10"), new BigDecimal("1.2775"), new BigDecimal("2.5"), new BigDecimal("0")}; // 10% per 365 days

    Date previousLoopIterationDate = new Date();
    
    BigDecimal[] depositFunds = new BigDecimal[currencyArray.length];
    Arrays.fill(depositFunds, BigDecimal.ZERO);

    double[] estimatedAccumulatedInterest = new double[currencyArray.length];
    Arrays.fill(estimatedAccumulatedInterest, 0.0);

    
    while (true) {
      try {
        BitfinexBalancesResponse[] balances = accountService.getBitfinexAccountInfo();
        BitfinexOfferStatusResponse[] activeOffers = tradeService.getBitfinexOpenOffers();
        BitfinexCreditResponse[] activeCredits = tradeService.getBitfinexActiveCredits();

        currencyStart:

          for (int currencyIndex=0; currencyIndex<currencyArray.length; currencyIndex++)
          {
        	final String currency = currencyArray[currencyIndex];
        	final BigDecimal maxRate = maxRateArray[currencyIndex];
        	final BigDecimal minRate = minRateArray[currencyIndex];

        	
            for (BitfinexBalancesResponse balance : balances) {
              if ("deposit".equalsIgnoreCase(balance.getType()) && currency.equalsIgnoreCase(balance.getCurrency())) {
                if (balance.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                  continue currencyStart;
                } else {
                  break;
                }
              } else if ( (balance.toString()).equalsIgnoreCase((balances[balances.length-1]).toString()) ) {
                continue currencyStart;
              }
            }

			System.out.println("*******************************************************************");
		    System.out.println(currency);

		    
    		BitfinexLend[] lends = marketDataService.getBitfinexLends(currency, 0, 1);
    		
    		BigDecimal minFunds = MIN_FUNDS_USD;
            if (!currency.equalsIgnoreCase("USD")) {
              BitfinexTicker ticker = marketDataService.getBitfinexTicker((currency).concat("USD"));
         	  minFunds = MIN_FUNDS_USD.divide(ticker.getBid(), 3, BigDecimal.ROUND_UP);
         	}

	        Date currentLoopIterationDate = new Date();
     	    BigDecimal activeCreditAmount = BigDecimal.ZERO;
        	double activeCreditInterest = 0.0;
        	final BigDecimal FRR = lends[0].getRate();

        	for (BitfinexCreditResponse credit : activeCredits) {
          	  if (currency.equalsIgnoreCase(credit.getCurrency())) {
      	        activeCreditAmount = activeCreditAmount.add(credit.getAmount());
            	BigDecimal creditRate = credit.getRate();

            	// Since we do not allow rates below minRate (which should be greater than zero)
            	// any 0 rate active credit is assumed to be at the flash return rate
            	if (BigDecimal.ZERO.compareTo(creditRate) == 0)
              	  creditRate = FRR;

            	activeCreditInterest = activeCreditInterest + credit.getAmount().doubleValue() * (creditRate.doubleValue() / 365 / 100) // rate per day in whole number terms (not percentage)
                	* ((double) (currentLoopIterationDate.getTime() - previousLoopIterationDate.getTime()) / millisecondsInDay);
          	  }
        	}

        	previousLoopIterationDate = currentLoopIterationDate;

        	BigDecimal activeOfferAmount = BigDecimal.ZERO;
        	BigDecimal activeOfferRate = BigDecimal.ZERO;
        	boolean activeOfferFrr = false;

        	for (BitfinexOfferStatusResponse offer : activeOffers) {
              if (currency.equalsIgnoreCase(offer.getCurrency()) && ("lend".equalsIgnoreCase(offer.getDirection())) ) {
                activeOfferAmount = activeOfferAmount.add(offer.getRemainingAmount());
                activeOfferRate = offer.getRate();
                activeOfferFrr = BigDecimal.ZERO.compareTo(offer.getRate()) == 0;
              }
        	}

        	for (BitfinexBalancesResponse balance : balances) {
          	  if ("deposit".equalsIgnoreCase(balance.getType()) && currency.equalsIgnoreCase(balance.getCurrency())) {
                if (depositFunds[currencyIndex].compareTo(balance.getAmount()) == 0) {
              	  estimatedAccumulatedInterest[currencyIndex] = estimatedAccumulatedInterest[currencyIndex] + activeCreditInterest;
              	  System.out.println("Estimated total accrued interest " + estimatedAccumulatedInterest[currencyIndex]);
            	} else {
              	  System.out.println("BFX paid " + (balance.getAmount().subtract(depositFunds[currencyIndex])) + " (post-fees) with the estimate of " + estimatedAccumulatedInterest[currencyIndex] + " (pre-fees)");
              	  depositFunds[currencyIndex] = balance.getAmount();
              	  estimatedAccumulatedInterest[currencyIndex] = 0.0;
                }
          	  }
        	}

        	BigDecimal inactiveFunds = depositFunds[currencyIndex].subtract(activeCreditAmount);
        	
        	// If we have the minimum or more of inactive funding ($50 USD), get data and go through calculation
        	if (inactiveFunds.compareTo(minFunds) >= 0) {
          	  BigDecimal bestBidRate = BigDecimal.ZERO;
          	  double prevTimestamp = (((double) (new Date()).getTime()) / 1000) - 60 * 10;
          	  boolean bidFrr = false;
          	  BitfinexLendDepth book = marketDataService.getBitfinexLendBook(currency, 5000, 5000);

          	  for (BitfinexLendLevel bidLevel : book.getBids()) {
            	// Ignore any bids below our minimum rate
            	if (bidLevel.getRate().compareTo(minRate) < 0) {
              	  continue;
            	}

            	if (bidLevel.getRate().compareTo(bestBidRate) > 0) {
              	  bestBidRate = bidLevel.getRate();

              	  if ("Yes".equalsIgnoreCase(bidLevel.getFrr())) {
                    bidFrr = true;
              	  } else {
                    bidFrr = false;
              	  }
                }
          	  }

          	  // If the FRR is demanded by buyers and our current order differs, send an order for FRR
          	  if (bidFrr && !matchesCurrentOrder(activeOfferFrr, activeOfferAmount, activeOfferRate, true, inactiveFunds, BigDecimal.ZERO)) {

                // Cancel existing orders and send new FRR order
                cancelPreviousAndSendNewOrder(tradeService, activeOffers, true, inactiveFunds, BigDecimal.ZERO, FRR, currency);

          	  } else { // flash return rate demanded by sellers, send a competitive fixed rate order
                BigDecimal bestAskOutsideBestBid = maxRate;
                BigDecimal secondBestAskOutsideBestBid = maxRate;
                BigDecimal bestAskOutsideBestBidAmount = BigDecimal.ZERO;
                boolean bestAskFrr = false;

                for (BitfinexLendLevel askLevel : book.getAsks()) {
                  // Ignore any asks below our minimum rate or newer than some arbitrary time period
                  if (askLevel.getRate().compareTo(minRate) < 0 || askLevel.getTimestamp() > prevTimestamp) {
                  continue;
                }

                if (askLevel.getRate().compareTo(bestBidRate) > 0) {
                  if (askLevel.getRate().compareTo(bestAskOutsideBestBid) < 0) {
                    secondBestAskOutsideBestBid = bestAskOutsideBestBid;
                    bestAskOutsideBestBid = askLevel.getRate();
                    bestAskOutsideBestBidAmount = BigDecimal.ZERO;

                    if ("Yes".equals(askLevel.getFrr())) {
                      bestAskFrr = true;
                    } else {
                      bestAskFrr = false;
                    }
                  }

                  // Add to the amount if we've found the best ask
                  if (askLevel.getRate().compareTo(bestAskOutsideBestBid) == 0) {
                    bestAskOutsideBestBidAmount = bestAskOutsideBestBidAmount.add(askLevel.getAmount());
                  }
                }
              }

              // If the best offer is FRR, just sit with everyone else
              if (bestAskFrr && !matchesCurrentOrder(activeOfferFrr, activeOfferAmount, activeOfferRate, true, inactiveFunds, BigDecimal.ZERO)) {
                // Cancel existing orders and send new FRR order
                cancelPreviousAndSendNewOrder(tradeService, activeOffers, true, inactiveFunds, BigDecimal.ZERO, FRR, currency);

              } else if (!bestAskFrr) {
                // Best ask is not FRR, we need to send a competitive fixed rate
                System.out.println("Comparing best ask outside best bid amount " + bestAskOutsideBestBidAmount + " with our offer amount " + activeOfferAmount);
                if (bestAskOutsideBestBidAmount.compareTo(activeOfferAmount) == 0) {
                  // Don't stay out there alone
                  // Join second best ask outside of best bid
                  cancelPreviousAndSendNewOrder(tradeService, activeOffers, false, inactiveFunds, secondBestAskOutsideBestBid, FRR, currency);
                } else if (!matchesCurrentOrder(activeOfferFrr, activeOfferAmount, activeOfferRate, false, inactiveFunds, bestAskOutsideBestBid)) {
                  // Join best ask outside of best bid
                  cancelPreviousAndSendNewOrder(tradeService, activeOffers, false, inactiveFunds, bestAskOutsideBestBid, FRR, currency);
                } else {
                  System.out.println("Matched previous isFrr: " + activeOfferFrr + " amount: " + activeOfferAmount + " rate: " + activeOfferRate);
                }
              } else {
                System.out.println("Matched previous isFrr: " + activeOfferFrr + " amount: " + activeOfferAmount + " rate: " + activeOfferRate);
              }
            }

          } else {
            System.out.println("Difference " + inactiveFunds + " not enough to post order (minimum " + minFunds + ")");
          }
        } // currency loop

      } catch (IOException e1) {
        e1.printStackTrace();
      } catch (ExchangeException e) {
        e.printStackTrace();
      }

      try {
        Thread.sleep(20000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private static boolean matchesCurrentOrder(boolean currentFrr, BigDecimal currentAmount, BigDecimal currentRate, boolean newFrr, BigDecimal newAmount, BigDecimal newRate) {

    System.out.println("Comparing currentFrr: " + currentFrr + " with newFrr: " + newFrr);
    if (currentFrr != newFrr)
      return false;

    System.out.println("Comparing currentAmount: " + currentAmount + " with newAmount: " + newAmount);
    if (currentAmount.compareTo(newAmount) != 0)
      return false;

    System.out.println("Comparing currentRate: " + currentRate + " with newRate: " + newRate);
    if (currentRate.compareTo(newRate) != 0)
      return false;

    return true;
  }

  private static void cancelPreviousAndSendNewOrder(BitfinexTradeServiceRaw tradeService, BitfinexOfferStatusResponse[] activeOffers, boolean isFrr, BigDecimal amount, BigDecimal rate, BigDecimal FRR, String currency) throws IOException {
    // Cancel existing orders
    if (activeOffers.length != 0) {
      for (BitfinexOfferStatusResponse offer : activeOffers) {
    	if (currency.equalsIgnoreCase(offer.getCurrency()) && ("lend".equalsIgnoreCase(offer.getDirection())) ) {
          System.out.println("Cancelling " + offer.toString());
          tradeService.cancelBitfinexOffer(Integer.toString(offer.getId()));
        }
      }
    } else {
      System.out.println("Found no previous order to cancel");
    }

    if (isFrr) {
      FloatingRateLoanOrder order = new FloatingRateLoanOrder(OrderType.ASK, currency, amount, 30, "", null, BigDecimal.ZERO);
      System.out.println("Sending " + order.toString());
      tradeService.placeBitfinexFloatingRateLoanOrder(order, BitfinexOrderType.MARKET);
    } else {
      // Set the day period for somewhere between 2 and 30 days. We compare our order's rate to the flash return rate.
      // If we're at or below the FRR, set the day period to 2. If we're above, use the ratio of our rate to the FRR to
      // determine how many days we should offer with a cap of 30 using: (ourRate - FRR) / FRR * 30
      int dayPeriod = Math.max(Math.min((int) ((rate.doubleValue() - FRR.doubleValue()) / FRR.doubleValue() * 30), 30), 2);

      FixedRateLoanOrder order = new FixedRateLoanOrder(OrderType.ASK, currency, amount, dayPeriod, "", null, rate);
      System.out.println("Sending " + order.toString() + ", rate=" + rate);
      tradeService.placeBitfinexFixedRateLoanOrder(order, BitfinexOrderType.LIMIT);
    }
  }
}
