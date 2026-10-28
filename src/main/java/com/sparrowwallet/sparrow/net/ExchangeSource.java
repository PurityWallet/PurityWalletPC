// Modified for PurityWallet on 2026-10-27; see LICENSE and NOTICE.
package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.event.ExchangeRatesUpdatedEvent;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.girod.javafx.svgimage.SVGImage;
import org.girod.javafx.svgimage.SVGLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;

public enum ExchangeSource {
    NONE("None", null) {
        @Override
        public List<Currency> getSupportedCurrencies() {
            return Collections.emptyList();
        }

        @Override
        public Double getExchangeRate(Currency currency) {
            return null;
        }

        @Override
        public Map<Date, Double> getHistoricalExchangeRates(Currency currency, Date start, Date end) {
            return Collections.emptyMap();
        }
    };

    private static final Logger log = LoggerFactory.getLogger(ExchangeSource.class);

    private final String name;
    private final String description;

    ExchangeSource(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public abstract List<Currency> getSupportedCurrencies();

    public abstract Double getExchangeRate(Currency currency);

    public abstract Map<Date, Double> getHistoricalExchangeRates(Currency currency, Date start, Date end);

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name;
    }

    public SVGImage getSVGImage() {
        try {
            URL url = AppServices.class.getResource("/image/exchangesource/" + name.toLowerCase(Locale.ROOT) + "-icon.svg");
            if(url != null) {
                return SVGLoader.load(url);
            }
        } catch(Exception e) {
            log.error("Could not load exchange source image for " + name);
        }

        return null;
    }

    public static class CurrenciesService extends Service<List<Currency>> {
        private final ExchangeSource exchangeSource;

        public CurrenciesService(ExchangeSource exchangeSource) {
            this.exchangeSource = exchangeSource;
        }

        @Override
        protected Task<List<Currency>> createTask() {
            return new Task<>() {
                protected List<Currency> call() {
                    return exchangeSource.getSupportedCurrencies();
                }
            };
        }
    }

    public static class RatesService extends ScheduledService<ExchangeRatesUpdatedEvent> {
        private final ExchangeSource exchangeSource;
        private final Currency selectedCurrency;

        public RatesService(ExchangeSource exchangeSource, Currency selectedCurrency) {
            this.exchangeSource = exchangeSource;
            this.selectedCurrency = selectedCurrency;
        }

        protected Task<ExchangeRatesUpdatedEvent> createTask() {
            return new Task<>() {
                protected ExchangeRatesUpdatedEvent call() {
                    Double rate = exchangeSource.getExchangeRate(selectedCurrency);
                    return new ExchangeRatesUpdatedEvent(selectedCurrency, rate);
                }
            };
        }

        public ExchangeSource getExchangeSource() {
            return exchangeSource;
        }
    }

}
