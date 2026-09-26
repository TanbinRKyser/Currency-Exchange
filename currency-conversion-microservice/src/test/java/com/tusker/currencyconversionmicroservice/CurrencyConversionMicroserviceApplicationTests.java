package com.tusker.currencyconversionmicroservice;

import com.tusker.currencyconversionmicroservice.model.CurrencyConversion;
import com.tusker.currencyconversionmicroservice.service.CurrencyConversionService;
import com.tusker.currencyconversionmicroservice.service.CurrencyExchangeProxy;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CurrencyConversionMicroserviceApplicationTests {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

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
	void getCurrencyConversionFeign_whenExchangeUnavailable_returns503() throws Exception {
		Request request = Request.create(
				Request.HttpMethod.GET,
				"http://exchange:8000/currency-exchange/from/USD/to/BDT",
				Map.of(),
				null,
				StandardCharsets.UTF_8
		);

		RetryableException unavailable = new RetryableException(
				-1,
				"Exchange unavailable",
				Request.HttpMethod.GET,
				(Long) null,
				request
		);

		when(currencyExchangeProxy.retrieveExchangeValue("USD", "BDT"))
				.thenThrow(unavailable);

		mockMvc.perform(get(
						"/currency-conversion-feign/from/{from}/to/{to}/amount/{amount}",
						"USD", "BDT", 100
				).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail")
						.value("Currency exchange service is temporarily unavailable"));

		verify(currencyExchangeProxy).retrieveExchangeValue("USD", "BDT");
	}

	@Test
	void whenCircuitBreakerForcedOpen_shouldReturn503AndNotCallProxy() throws Exception {
		CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("exchange");
		breaker.transitionToForcedOpenState();

		try {
			mockMvc.perform(get("/currency-conversion-feign/from/USD/to/BDT/amount/100")
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.detail")
					.value("Currency exchange service is temporarily unavailable"));

			verifyNoInteractions(currencyExchangeProxy);
		} finally {
			breaker.reset();
		}
	}

	@Test
	void whenFiveFailuresOccur_circuitBreakerOpensAndRejectsSixthRequest() throws Exception {
		CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("exchange");
		breaker.reset();

		try {
			// Construct a real Feign RetryableException
			Request request = Request.create(
					Request.HttpMethod.GET,
					"http://localhost:8000/currency-exchange/from/USD/to/BDT",
					Collections.emptyMap(),
					null,
					StandardCharsets.UTF_8
			);

			RetryableException retryableException = new RetryableException(
					503,
					"Service Unavailable",
					Request.HttpMethod.GET,
					(Long) null,
					request
			);

			when(currencyExchangeProxy.retrieveExchangeValue("USD", "BDT"))
					.thenThrow(retryableException);

			// Make 5 requests causing failures and driving the breaker to OPEN
			for (int i = 0; i < 5; i++) {
				mockMvc.perform(get("/currency-conversion-feign/from/USD/to/BDT/amount/100")
								.accept(MediaType.APPLICATION_JSON))
						.andExpect(status().isServiceUnavailable())
						.andExpect(jsonPath("$.detail")
								.value("Currency exchange service is temporarily unavailable"));
			}

			// Assert the breaker has transitioned to OPEN
			assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

			// 6th request: rejected directly by the open circuit breaker
			mockMvc.perform(get("/currency-conversion-feign/from/USD/to/BDT/amount/100")
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isServiceUnavailable());

			// Verify the proxy was called only for the initial 5 attempts
			verify(currencyExchangeProxy, times(5)).retrieveExchangeValue("USD", "BDT");

		} finally {
			breaker.reset();
		}
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

	@Test
	void getCurrencyConversion_whenExchangeUnavailable_returns503() throws Exception {
		MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

		mockServer.expect(requestTo("http://localhost:8000/currency-exchange/from/USD/to/BDT"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withException(new IOException("Connection refused")));

		mockMvc.perform(get("/currency-conversion/from/USD/to/BDT/amount/100")
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail")
						.value("Currency exchange service is temporarily unavailable"));

		mockServer.verify();
	}
}
