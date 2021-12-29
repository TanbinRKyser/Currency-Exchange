package com.tusker.currencyexchangeservice.controller;

import com.tusker.currencyexchangeservice.model.CurrencyExchange;
import com.tusker.currencyexchangeservice.service.CurrencyExchangeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrencyExchangeController {

    private static final Logger LOGGER = LoggerFactory.getLogger( CurrencyExchangeController.class );

    private final Environment environment;
    private final CurrencyExchangeService currencyExchangeService;

    public CurrencyExchangeController( Environment environment, CurrencyExchangeService currencyExchange ) {
        this.environment = environment;
        this.currencyExchangeService = currencyExchange;
    }

    @GetMapping("currency-exchange/from/{from}/to/{to}")
    public CurrencyExchange retrieveExchangeValue( @PathVariable String from, @PathVariable String to ){

        LOGGER.info( "retrieve exchange value added from {} to {}", from, to );

        CurrencyExchange currencyExchange = currencyExchangeService.getCurrencyFromAndTo( from, to );

        String port = environment.getProperty( "local.server.port" );

        // change kubernetes
        String host = environment.getProperty( "HOSTNAME" );
        String version = "v12";

        currencyExchange.setEnvironment( port + " " + version + " " + host );

        return currencyExchange;
    }
}
