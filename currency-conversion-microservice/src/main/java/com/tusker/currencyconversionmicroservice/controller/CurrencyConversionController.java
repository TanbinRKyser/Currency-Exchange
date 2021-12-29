package com.tusker.currencyconversionmicroservice.controller;

import com.tusker.currencyconversionmicroservice.model.CurrencyConversion;
import com.tusker.currencyconversionmicroservice.service.CurrencyConversionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
public class CurrencyConversionController {

    private final CurrencyConversionService  currencyConversionService;


    public CurrencyConversionController( CurrencyConversionService currencyConversionService ) {
        this.currencyConversionService = currencyConversionService;
    }

    @GetMapping("/currency-conversion/from/{from}/to/{to}/amount/{amount}")
    public CurrencyConversion getConvertedCurrency( @PathVariable String from,
                                                    @PathVariable String to,
                                                    @PathVariable BigDecimal amount ){

        return currencyConversionService.convertCurrency( from, to, amount );
    }

    @GetMapping("/currency-conversion-feign/from/{from}/to/{to}/amount/{amount}")
    public CurrencyConversion getConvertedCurrencyUsingFeign( @PathVariable String from,
                                                    @PathVariable String to,
                                                    @PathVariable BigDecimal amount ){

        return currencyConversionService.convertCurrencyUsingFeign( from, to, amount );
    }
}
