package com.tusker.currencyexchangeservice.service;

import com.tusker.currencyexchangeservice.model.CurrencyExchange;
import com.tusker.currencyexchangeservice.repository.CurrencyExchangeRepository;
import org.springframework.stereotype.Component;

@Component
public class CurrencyExchangeService {

    private final CurrencyExchangeRepository currencyExchangeRepository;

    public CurrencyExchangeService( CurrencyExchangeRepository currencyExchangeRepository ) {
        this.currencyExchangeRepository = currencyExchangeRepository;
    }


    public CurrencyExchange getCurrencyFromAndTo( String from, String to ){
        CurrencyExchange currencyExchange = currencyExchangeRepository.fromAndTo( from, to );

        if( currencyExchange == null )
            throw new RuntimeException("Unable to find data for " + from + " to " + to );

        return currencyExchange;
    }
}
