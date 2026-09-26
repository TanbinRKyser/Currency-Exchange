package com.tusker.currencyconversionmicroservice.service;

import com.tusker.currencyconversionmicroservice.model.CurrencyConversion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;

@Component
public class CurrencyConversionService {

    private final RestTemplate restTemplate;
    private final CurrencyExchangeProxy currencyExchangeProxy;
    private final String currencyExchangeUri;
    private Logger logger = LoggerFactory.getLogger( CurrencyConversionService.class );

    public CurrencyConversionService(RestTemplate restTemplate, CurrencyExchangeProxy currencyExchangeProxy,
                                     @Value("${CURRENCY_EXCHANGE_URI:http://localhost}") String currencyExchangeUri ) {
        this.restTemplate = restTemplate;
        this.currencyExchangeProxy = currencyExchangeProxy;
        this.currencyExchangeUri = currencyExchangeUri;
    }

    public CurrencyConversion convertCurrency( String from, String to, BigDecimal amount ) {

        logger.info( "convertCurrency is called from {} to {} with {}", from, to, amount );

        HashMap<String,String> uriVariables = new HashMap<>();
        uriVariables.put( "from", from);
        uriVariables.put( "to", to);

        ResponseEntity<CurrencyConversion> responseEntity = restTemplate.getForEntity(
                currencyExchangeUri + ":8000/currency-exchange/from/{from}/to/{to}",
                CurrencyConversion.class,
                uriVariables );

        CurrencyConversion currencyConversion = responseEntity.getBody();

        return new CurrencyConversion( currencyConversion.getId(),
                from, to, amount,
                currencyConversion.getConversionMultiple(),
                amount.multiply( currencyConversion.getConversionMultiple() ),
                currencyConversion.getEnvironment()
        );
    }

    public CurrencyConversion convertCurrencyUsingFeign( String from, String to, BigDecimal amount ) {

        CurrencyConversion currencyConversion = currencyExchangeProxy.retrieveExchangeValue( from, to);

        return new CurrencyConversion( currencyConversion.getId(),
                from, to, amount,
                currencyConversion.getConversionMultiple(),
                amount.multiply( currencyConversion.getConversionMultiple() ),
                currencyConversion.getEnvironment() + " " + "feign"
        );
    }
}
