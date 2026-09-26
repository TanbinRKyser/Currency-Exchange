package com.tusker.currencyconversionmicroservice;

import com.tusker.currencyconversionmicroservice.model.CurrencyConversion;
import com.tusker.currencyconversionmicroservice.service.CurrencyConversionService;
import com.tusker.currencyconversionmicroservice.service.CurrencyExchangeProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CurrencyConversionMicroserviceApplicationTests {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private RestTemplate restTemplate;

    @MockitoBean
	private CurrencyExchangeProxy currencyExchangeProxy;
	private CurrencyConversionService currencyConversionService;

	@Test
	void contextLoads() {
	}

	@BeforeEach
	void setUp() {
		currencyConversionService = new CurrencyConversionService(
				restTemplate, currencyExchangeProxy, "http://unused"
		);
	}


	@Test
	void convertCurrencyUsingFeign_shouldCalculateTotalAndSetFeignEnvironment() {
		// Arrange
		String from = "USD";
		String to = "BDT";
		BigDecimal quantity = new BigDecimal("100");
		BigDecimal conversionMultiple = new BigDecimal("85.41");

		CurrencyConversion mockResponse = new CurrencyConversion(
				10001L,
				from,
				to,
				quantity,
				conversionMultiple,
				BigDecimal.ZERO,
				"8000"
		);

		when(currencyExchangeProxy.retrieveExchangeValue(from, to)).thenReturn(mockResponse);

		// Act
		CurrencyConversion result = currencyConversionService.convertCurrencyUsingFeign(from, to, quantity);

		// Assert
		assertNotNull(result);
		assertEquals(from, result.getFrom());
		assertEquals(to, result.getTo());
		assertEquals(new BigDecimal("8541.00"), result.getTotal());
		assertTrue(result.getEnvironment().endsWith("feign"));

		// Verify
		verify(currencyExchangeProxy).retrieveExchangeValue(from, to);
	}

	@Test
	void getCurrencyConversionFeign_shouldReturnHttp200AndCalculatedValues() throws Exception {
		// Arrange
		String from = "USD";
		String to = "BDT";
		BigDecimal multiple = new BigDecimal("85.41");

		CurrencyConversion mockProxyResponse = new CurrencyConversion(
				10001L,
				from,
				to,
				null,
				multiple,
				null,
				"8000"
		);

		when(currencyExchangeProxy.retrieveExchangeValue(from, to)).thenReturn(mockProxyResponse);

		// Act & Assert
		mockMvc.perform(get("/currency-conversion-feign/from/{from}/to/{to}/amount/{amount}", from, to, 100)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(10001))
				.andExpect(jsonPath("$.from").value(from))
				.andExpect(jsonPath("$.to").value(to))
				.andExpect(jsonPath("$.conversionMultiple").value(85.41))
				.andExpect(jsonPath("$.amount").value(100))
				.andExpect(jsonPath("$.total").value(8541.00))
				.andExpect(jsonPath("$.environment").value("8000 feign"));

		// Verify
		verify(currencyExchangeProxy).retrieveExchangeValue(from, to);
	}

	@Test
	void calculateCurrencyConversion_shouldInvokeExchangeServiceAndReturnTotal() throws Exception {
		// Arrange
		String mockResponseJson = """
                {
                    "id": 10001,
                    "from": "USD",
                    "to": "BDT",
                    "conversionMultiple": 85.41,
                    "environment": "exchange-stub"
                }
                """;
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

		// Expect outbound GET request via RestTemplate
		mockServer.expect(requestTo("http://localhost:8000/currency-exchange/from/USD/to/BDT"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

		// Act & Assert via MockMvc
		mockMvc.perform(get("/currency-conversion/from/USD/to/BDT/amount/100")
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(10001))
				.andExpect(jsonPath("$.from").value("USD"))
				.andExpect(jsonPath("$.to").value("BDT"))
				.andExpect(jsonPath("$.conversionMultiple").value(85.41))
				.andExpect(jsonPath("$.amount").value(100))
				.andExpect(jsonPath("$.total").value(8541.00))
				.andExpect(jsonPath("$.environment").value("exchange-stub"));

		// Verify outbound HTTP call was made
		mockServer.verify();
	}
}
