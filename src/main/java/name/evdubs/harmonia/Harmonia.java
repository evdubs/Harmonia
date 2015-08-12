package name.evdubs.harmonia;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.RegexReplacement;

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
  private static final String LOGGER_NAME = "HarmoniaLogger";
  private static Logger log = LogManager.getLogger(LOGGER_NAME);

  public static void main(String[] args) {
    // Set up logging
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration config = ctx.getConfiguration();
    RollingFileAppender rfa = RollingFileAppender.createAppender("/var/tmp/Harmonia.log", "/var/tmp/Harmonia.log.%d{yyyy-MM-dd}", "true", "HarmoniaRollingFileAppender", "true", "8192", "true",
        TimeBasedTriggeringPolicy.createPolicy("1", "true"), DefaultRolloverStrategy.createStrategy("365", "1", "1", "0", config),
        PatternLayout.createLayout(PatternLayout.SIMPLE_CONVERSION_PATTERN, config, RegexReplacement.createRegexReplacement(Pattern.compile(""), ""), Charset.defaultCharset(), true, false, "", ""),
        null, "true", "false", null, config);
    rfa.start();
    config.addAppender(rfa);
    AppenderRef ref = AppenderRef.createAppenderRef("File", null, null);
    AppenderRef[] refs = new AppenderRef[] { ref };
    LoggerConfig loggerConfig = LoggerConfig.createLogger("true", Level.INFO, LOGGER_NAME, "true", refs, new Property[0], config, (Filter) null);
    loggerConfig.addAppender(rfa, null, null);
    config.addLogger(LOGGER_NAME, loggerConfig);
    ctx.updateLoggers();

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
      log.info("Could not read in keys; exiting.");
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

    BigDecimal minFunds = new BigDecimal("50"); // minimum amount needed (USD) to lend
    BigDecimal maxRate = new BigDecimal("2555"); // 7% per day * 365 days
    BigDecimal minRate = new BigDecimal("10"); // 10% per 365 days
    double millisecondsInDay = 86400000.0;

    BigDecimal depositFunds = BigDecimal.ZERO;
    double estimatedAccumulatedInterest = 0.0;
    Date previousLoopIterationDate = new Date();

    while (true) {
      try {
        BitfinexBalancesResponse[] balances = accountService.getBitfinexAccountInfo();
        BitfinexOfferStatusResponse[] activeOffers = tradeService.getBitfinexOpenOffers();
        BitfinexCreditResponse[] activeCredits = tradeService.getBitfinexActiveCredits();
        BitfinexLend[] lends = marketDataService.getBitfinexLends("USD", 0, 1);

        Date currentLoopIterationDate = new Date();
        BigDecimal activeCreditAmount = BigDecimal.ZERO;
        double activeCreditInterest = 0.0;
        BigDecimal frr = lends[0].getRate();

        for (BitfinexCreditResponse credit : activeCredits) {
          if ("USD".equalsIgnoreCase(credit.getCurrency())) {
            activeCreditAmount = activeCreditAmount.add(credit.getAmount());
            BigDecimal creditRate = credit.getRate();

            // Since we do not allow rates below minRate (which should be greater than zero)
            // any 0 rate active credit is assumed to be at the flash return rate
            if (BigDecimal.ZERO.compareTo(creditRate) == 0)
              creditRate = frr;

            activeCreditInterest = activeCreditInterest + credit.getAmount().doubleValue() * (creditRate.doubleValue() / 365 / 100) // rate per day in whole number terms (not percentage)
                * ((double) (currentLoopIterationDate.getTime() - previousLoopIterationDate.getTime()) / millisecondsInDay);
          }
        }

        previousLoopIterationDate = currentLoopIterationDate;

        BigDecimal activeOfferAmount = BigDecimal.ZERO;
        BigDecimal activeOfferRate = BigDecimal.ZERO;
        boolean activeOfferFrr = false;

        for (BitfinexOfferStatusResponse offer : activeOffers) {
          if ("USD".equalsIgnoreCase(offer.getCurrency()) && ("lend".equalsIgnoreCase(offer.getDirection()))) {
            activeOfferAmount = activeOfferAmount.add(offer.getRemainingAmount());
            activeOfferRate = offer.getRate();
            activeOfferFrr = BigDecimal.ZERO.compareTo(offer.getRate()) == 0;
          }
        }

        for (BitfinexBalancesResponse balance : balances) {
          if ("deposit".equalsIgnoreCase(balance.getType()) && "USD".equalsIgnoreCase(balance.getCurrency())) {
            if (depositFunds.compareTo(balance.getAmount()) == 0) {
              estimatedAccumulatedInterest = estimatedAccumulatedInterest + activeCreditInterest;
              log.info("Estimated total accrued interest " + estimatedAccumulatedInterest);
            } else {
              double bfxFee = 0.0;
              if (estimatedAccumulatedInterest != 0.0) {
                bfxFee = 1 - (balance.getAmount().subtract(depositFunds).doubleValue() / estimatedAccumulatedInterest);
              }
              log.info("BFX paid " + (balance.getAmount().subtract(depositFunds)) + " (post-fees) with the estimate of " + estimatedAccumulatedInterest + " (pre-fees) implying an effective fee of "
                  + bfxFee);
              depositFunds = balance.getAmount();
              estimatedAccumulatedInterest = 0.0;
            }
          }
        }

        BigDecimal inactiveFunds = depositFunds.subtract(activeCreditAmount);

        // If we have the minimum or more of inactive funding ($50 USD), get data and go through calculation
        if (inactiveFunds.compareTo(minFunds) >= 0) {
          BigDecimal bestBidRate = BigDecimal.ZERO;
          double prevTimestamp = (((double) (new Date()).getTime()) / 1000) - 60 * 10;
          boolean bidFrr = false;
          BitfinexLendDepth book = marketDataService.getBitfinexLendBook("USD", 5000, 5000);

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
            cancelPreviousAndSendNewOrder(tradeService, activeOffers, true, inactiveFunds, BigDecimal.ZERO, frr);

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
              cancelPreviousAndSendNewOrder(tradeService, activeOffers, true, inactiveFunds, BigDecimal.ZERO, frr);

            } else if (!bestAskFrr) {
              // Best ask is not FRR, we need to send a competitive fixed rate
              log.info("Comparing best ask outside best bid amount " + bestAskOutsideBestBidAmount + " with our offer amount " + activeOfferAmount);
              if (bestAskOutsideBestBidAmount.compareTo(activeOfferAmount) == 0) {
                // Don't stay out there alone
                // Join second best ask outside of best bid
                cancelPreviousAndSendNewOrder(tradeService, activeOffers, false, inactiveFunds, secondBestAskOutsideBestBid, frr);
              } else if (!matchesCurrentOrder(activeOfferFrr, activeOfferAmount, activeOfferRate, false, inactiveFunds, bestAskOutsideBestBid)) {
                // Join best ask outside of best bid
                cancelPreviousAndSendNewOrder(tradeService, activeOffers, false, inactiveFunds, bestAskOutsideBestBid, frr);
              } else {
                log.info("Matched previous isFrr: " + activeOfferFrr + " amount: " + activeOfferAmount + " rate: " + activeOfferRate);
              }
            } else {
              log.info("Matched previous isFrr: " + activeOfferFrr + " amount: " + activeOfferAmount + " rate: " + activeOfferRate);
            }
          }

        } else {
          log.info("Difference " + inactiveFunds + " not enough to post order");
        }

      } catch (IOException e) {
        log.error(e);
      } catch (ExchangeException e) {
        log.error(e);
      }

      try {
        Thread.sleep(20000);
      } catch (InterruptedException e) {
        log.error(e);
      }
    }
  }

  private static boolean matchesCurrentOrder(boolean currentFrr, BigDecimal currentAmount, BigDecimal currentRate, boolean newFrr, BigDecimal newAmount, BigDecimal newRate) {

    log.info("Comparing currentFrr: " + currentFrr + " with newFrr: " + newFrr);
    if (currentFrr != newFrr)
      return false;

    log.info("Comparing currentAmount: " + currentAmount + " with newAmount: " + newAmount);
    if (currentAmount.compareTo(newAmount) != 0)
      return false;

    log.info("Comparing currentRate: " + currentRate + " with newRate: " + newRate);
    if (currentRate.compareTo(newRate) != 0)
      return false;

    return true;
  }

  private static void cancelPreviousAndSendNewOrder(BitfinexTradeServiceRaw tradeService, BitfinexOfferStatusResponse[] activeOffers, boolean isFrr, BigDecimal amount, BigDecimal rate, BigDecimal frr)
      throws IOException {
    // Cancel existing orders
    if (activeOffers.length != 0) {
      for (BitfinexOfferStatusResponse offer : activeOffers) {
        if ("USD".equalsIgnoreCase(offer.getCurrency()) && ("lend".equalsIgnoreCase(offer.getDirection()))) {
          log.info("Cancelling " + offer.toString());
          tradeService.cancelBitfinexOffer(Integer.toString(offer.getId()));
        }
      }
    } else {
      log.info("Found no previous order to cancel");
    }

    if (isFrr) {
      FloatingRateLoanOrder order = new FloatingRateLoanOrder(OrderType.ASK, "USD", amount, 30, "", null, BigDecimal.ZERO);
      log.info("Sending " + order.toString());
      tradeService.placeBitfinexFloatingRateLoanOrder(order, BitfinexOrderType.MARKET);
    } else {
      // Set the day period for somewhere between 2 and 30 days. We compare our order's rate to the flash return rate.
      // If we're at or below the FRR, set the day period to 2. If we're above, use the ratio of our rate to the FRR to
      // determine how many days we should offer with a cap of 30 using: (ourRate - frr) / frr * 30
      int dayPeriod = Math.max(Math.min((int) ((rate.doubleValue() - frr.doubleValue()) / frr.doubleValue() * 30), 30), 2);

      FixedRateLoanOrder order = new FixedRateLoanOrder(OrderType.ASK, "USD", amount, dayPeriod, "", null, rate);
      log.info("Sending " + order.toString() + ", rate=" + rate);
      tradeService.placeBitfinexFixedRateLoanOrder(order, BitfinexOrderType.LIMIT);
    }
  }
}
