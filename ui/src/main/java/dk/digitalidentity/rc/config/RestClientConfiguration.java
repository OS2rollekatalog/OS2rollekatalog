package dk.digitalidentity.rc.config;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.reactor.ssl.SSLBufferMode;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.xml.JacksonXmlHttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfiguration {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Bean(name = "defaultRestClient")
	public RestClient defaultRestClient() {
		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();

		managerBuilder.setDefaultSocketConfig(
			SocketConfig.custom().setSoTimeout(Timeout.ofMinutes(3)).build()
		);

		final CloseableHttpClient httpClient = HttpClients.custom()
			.setDefaultRequestConfig(createDefaultRequestConfig())
			.setConnectionManager(managerBuilder.build())
			// callers use stateless auth (Bearer tokens etc.) — skip the cookie store so we
			// don't emit WARN for upstream session cookies like Azure's ARRAffinity, whose
			// domain attribute does not match the request origin when fronted by a custom domain
			.disableCookieManagement()
			.build();

		final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(Duration.ofMinutes(3));
		requestFactory.setReadTimeout(Duration.ofMinutes(3));
		requestFactory.setHttpClient(httpClient);

		// configure message converters for XML handling
		return RestClient.builder()
			.requestFactory(requestFactory)
			.configureMessageConverters(conf -> {
				// touching configureMessageConverters at all suppresses the builder's own
				// registerDefaults(), so we have to ask for the defaults explicitly - without
				// this the converter list ends up holding only the Jaxb2 converter added below
				conf.registerDefaults();
				conf.configureMessageConvertersList(converters -> {
					converters.removeIf(converter -> converter.getClass().equals(JacksonXmlHttpMessageConverter.class));
					converters.add(new Jaxb2RootElementHttpMessageConverter());
				});
			})
			.build();
	}

	@Lazy(true) // we need to ensure this is LAZY due to CRaC
	@Bean(name = "kspCicsRestClient")
	public RestClient kspCicsRestClient() throws Exception {
		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();

		if (configuration.getIntegrations().getKspcics().isEnabled()) {
			final SSLContext sslContext = SSLContextBuilder.create()
				.loadKeyMaterial(
					ResourceUtils.getFile(configuration.getIntegrations().getKspcics().getKeystoreLocation()),
					configuration.getIntegrations().getKspcics().getKeystorePassword().toCharArray(),
					configuration.getIntegrations().getKspcics().getKeystorePassword().toCharArray())
				.build();

		    final TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(sslContext);

		    managerBuilder.setTlsSocketStrategy(tlsSocketStrategy);
		}

		managerBuilder.setDefaultSocketConfig(
			SocketConfig.custom().setSoTimeout(Timeout.ofMinutes(3)).build()
		);

		final CloseableHttpClient httpClient = HttpClients.custom()
			.setDefaultRequestConfig(createDefaultRequestConfig())
			.setConnectionManager(managerBuilder.build())
			.build();

		final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(Duration.ofMinutes(3));
		requestFactory.setReadTimeout(Duration.ofMinutes(3));
		requestFactory.setHttpClient(httpClient);

		// Configure with error handler that doesn't throw exceptions
		return RestClient.builder()
			.requestFactory(requestFactory)
			.defaultStatusHandler(new ResponseErrorHandler() {
				@Override
				public boolean hasError(ClientHttpResponse response) throws IOException {
					// returning false means no exception is ever thrown
					return false;
				}

				@Override
				public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
					// false above means we never call this method
				}
			})
			.build();
	}

	@Lazy(true) // we need to ensure this is lazy due to CRaC
	@Bean(name = "kombitRestClient")
	public RestClient kombitRestClient() throws Exception {
		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();

		if (configuration.getIntegrations().getKombit().isEnabled() &&
			StringUtils.hasLength(configuration.getIntegrations().getKombit().getKeystoreLocation())) {
			final SSLContext sslContext = SSLContextBuilder.create()
				.loadKeyMaterial(
					ResourceUtils.getFile(configuration.getIntegrations().getKombit().getKeystoreLocation()),
					configuration.getIntegrations().getKombit().getKeystorePassword().toCharArray(),
					configuration.getIntegrations().getKombit().getKeystorePassword().toCharArray())
				.build();

		    final TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(
		    	sslContext,
		    	new String[] { "TLSv1.2", "TLSv1.3" },
		    	new String[] { "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" },
		    	SSLBufferMode.STATIC,
		    	new DefaultHostnameVerifier()
		    );

		    managerBuilder.setTlsSocketStrategy(tlsSocketStrategy);
		}

		managerBuilder.setDefaultSocketConfig(
			SocketConfig.custom().setSoTimeout(Timeout.ofMinutes(3)).build()
		);

		final CloseableHttpClient httpClient = HttpClients.custom()
			.setDefaultRequestConfig(createDefaultRequestConfig())
			.setConnectionManager(managerBuilder.build())
			.build();

		final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(Duration.ofMinutes(3));
		requestFactory.setReadTimeout(Duration.ofMinutes(3));
		requestFactory.setHttpClient(httpClient);

		// No error handler - exceptions are thrown on error
		return RestClient.builder()
			.requestFactory(requestFactory)
			.build();
	}

	@Lazy(true) // we need to ensure this is lazy due to CRaC
	@Bean(name = "kombitTestRestClient")
	public RestClient kombitTestRestClient() throws Exception {
		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();

		if (configuration.getIntegrations().getKombit().isTestEnabled() &&
			StringUtils.hasLength(configuration.getIntegrations().getKombit().getTestKeystoreLocation())) {
			final SSLContext sslContext = SSLContextBuilder.create()
				.loadKeyMaterial(
					ResourceUtils.getFile(configuration.getIntegrations().getKombit().getTestKeystoreLocation()),
					configuration.getIntegrations().getKombit().getTestKeystorePassword().toCharArray(),
					configuration.getIntegrations().getKombit().getTestKeystorePassword().toCharArray())
				.build();

			
		    final TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(
		    	sslContext,
		    	new String[] { "TLSv1.2", "TLSv1.3" },
		    	new String[] { "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" },
		    	SSLBufferMode.STATIC,
		    	new DefaultHostnameVerifier()
		    );

		    managerBuilder.setTlsSocketStrategy(tlsSocketStrategy);
		}

		managerBuilder.setDefaultSocketConfig(
			SocketConfig.custom().setSoTimeout(Timeout.ofMinutes(3)).build()
		);

		final CloseableHttpClient httpClient = HttpClients.custom()
			.setDefaultRequestConfig(createDefaultRequestConfig())
			.setConnectionManager(managerBuilder.build())
			.build();

		final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(Duration.ofMinutes(3));
		requestFactory.setReadTimeout(Duration.ofMinutes(3));
		requestFactory.setHttpClient(httpClient);

		// No error handler - exceptions are thrown on error
		return RestClient.builder()
			.requestFactory(requestFactory)
			.build();
	}

	@Lazy(true) // we need to ensure this is lazy due to CRaC
	@Bean(name = "nemLoginRestClient")
	public RestClient nemLoginRestClient() throws Exception {
		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();

		if (configuration.getIntegrations().getNemLogin().isEnabled() &&
			StringUtils.hasLength(configuration.getIntegrations().getNemLogin().getKeystoreLocation()) &&
			StringUtils.hasLength(configuration.getIntegrations().getNemLogin().getKeystorePassword())) {
			final SSLContext sslContext = SSLContextBuilder.create()
				.loadKeyMaterial(
					ResourceUtils.getFile(configuration.getIntegrations().getNemLogin().getKeystoreLocation()),
					configuration.getIntegrations().getNemLogin().getKeystorePassword().toCharArray(),
					configuration.getIntegrations().getNemLogin().getKeystorePassword().toCharArray())
				.build();

		    final TlsSocketStrategy tlsSocketStrategy = new DefaultClientTlsStrategy(
		    	sslContext
		    );

		    managerBuilder.setTlsSocketStrategy(tlsSocketStrategy);
		}

		managerBuilder.setDefaultSocketConfig(
			SocketConfig.custom().setSoTimeout(Timeout.ofMinutes(3)).build()
		);

		final CloseableHttpClient httpClient = HttpClients.custom()
			.setDefaultRequestConfig(createDefaultRequestConfig())
			.setConnectionManager(managerBuilder.build())
			.disableCookieManagement()
			.build();

		final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(Duration.ofMinutes(3));
		requestFactory.setReadTimeout(Duration.ofMinutes(3));
		requestFactory.setHttpClient(httpClient);

		// Configure with error handler that doesn't throw exceptions
		return RestClient.builder()
			.requestFactory(requestFactory)
			.defaultStatusHandler(new ResponseErrorHandler() {
				@Override
				public boolean hasError(ClientHttpResponse response) throws IOException {
					return false;
				}

				@Override
				public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
					// No error handling - status codes are returned as-is
				}
			})
			.build();
	}

	// Helper method to create default request config with cookie handling
	private RequestConfig createDefaultRequestConfig() {
		return RequestConfig.custom().setCookieSpec(StandardCookieSpec.RELAXED).build();  // Empty config, timeouts set on requestFactory
	}
}
