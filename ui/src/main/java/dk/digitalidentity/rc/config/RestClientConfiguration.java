package dk.digitalidentity.rc.config;

import java.io.IOException;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
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
			.build();

		final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(Duration.ofMinutes(3));
		requestFactory.setReadTimeout(Duration.ofMinutes(3));
		requestFactory.setHttpClient(httpClient);

		// Configure message converters for XML handling
		return RestClient.builder()
			.requestFactory(requestFactory)
			.messageConverters(converters -> {
				converters.removeIf(converter ->
					converter.getClass().equals(MappingJackson2XmlHttpMessageConverter.class)
				);
				converters.add(new Jaxb2RootElementHttpMessageConverter());
			})
			.build();
	}

	@Bean(name = "kspCicsRestClient")
	public RestClient kspCicsRestClient() throws Exception {
		final TrustStrategy acceptingTrustStrategy = (X509Certificate[] _, String _) -> true;

		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();

		if (configuration.getIntegrations().getKspcics().isEnabled()) {
			final SSLContext sslContext = SSLContextBuilder.create()
				.loadKeyMaterial(
					ResourceUtils.getFile(configuration.getIntegrations().getKspcics().getKeystoreLocation()),
					configuration.getIntegrations().getKspcics().getKeystorePassword().toCharArray(),
					configuration.getIntegrations().getKspcics().getKeystorePassword().toCharArray())
				.loadTrustMaterial(acceptingTrustStrategy)
				.build();

			managerBuilder.setSSLSocketFactory(
				SSLConnectionSocketFactoryBuilder.create()
					.setSslContext(sslContext)
					.setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
					.build()
			);
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

	@Bean(name = "kombitRestClient")
	public RestClient kombitRestClient() throws Exception {
		final TrustStrategy acceptingTrustStrategy = (X509Certificate[] _, String _) -> true;

		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();

		if (configuration.getIntegrations().getKombit().isEnabled() &&
			StringUtils.hasLength(configuration.getIntegrations().getKombit().getKeystoreLocation())) {
			final SSLContext sslContext = SSLContextBuilder.create()
				.loadKeyMaterial(
					ResourceUtils.getFile(configuration.getIntegrations().getKombit().getKeystoreLocation()),
					configuration.getIntegrations().getKombit().getKeystorePassword().toCharArray(),
					configuration.getIntegrations().getKombit().getKeystorePassword().toCharArray())
				.loadTrustMaterial(acceptingTrustStrategy)
				.build();

			// Use special Kombit socket factory with specific ciphers and TLS versions
			managerBuilder.setSSLSocketFactory(
				SSLConnectionSocketFactoryBuilder.create()
					.setCiphers("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
					.setSslContext(sslContext)
					.setHostnameVerifier(new DefaultHostnameVerifier())
					.setTlsVersions(TLS.V_1_2, TLS.V_1_3)
					.build()
			);
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

	@Bean(name = "kombitTestRestClient")
	public RestClient kombitTestRestClient() throws Exception {
		final TrustStrategy acceptingTrustStrategy = (X509Certificate[] _, String _) -> true;

		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();

		if (configuration.getIntegrations().getKombit().isTestEnabled() &&
			StringUtils.hasLength(configuration.getIntegrations().getKombit().getTestKeystoreLocation())) {
			final SSLContext sslContext = SSLContextBuilder.create()
				.loadKeyMaterial(
					ResourceUtils.getFile(configuration.getIntegrations().getKombit().getTestKeystoreLocation()),
					configuration.getIntegrations().getKombit().getTestKeystorePassword().toCharArray(),
					configuration.getIntegrations().getKombit().getTestKeystorePassword().toCharArray())
				.loadTrustMaterial(acceptingTrustStrategy)
				.build();

			// Use special Kombit socket factory with specific ciphers and TLS versions
			managerBuilder.setSSLSocketFactory(
				SSLConnectionSocketFactoryBuilder.create()
					.setCiphers("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
					.setSslContext(sslContext)
					.setHostnameVerifier(new DefaultHostnameVerifier())
					.setTlsVersions(TLS.V_1_2, TLS.V_1_3)
					.build()
			);
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

	@Bean(name = "nemLoginRestClient")
	public RestClient nemLoginRestClient() throws Exception {
		final TrustStrategy acceptingTrustStrategy = (X509Certificate[] _, String _) -> true;

		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();

		if (configuration.getIntegrations().getNemLogin().isEnabled() &&
			StringUtils.hasLength(configuration.getIntegrations().getNemLogin().getKeystoreLocation()) &&
			StringUtils.hasLength(configuration.getIntegrations().getNemLogin().getKeystorePassword())) {
			final SSLContext sslContext = SSLContextBuilder.create()
				.loadKeyMaterial(
					ResourceUtils.getFile(configuration.getIntegrations().getNemLogin().getKeystoreLocation()),
					configuration.getIntegrations().getNemLogin().getKeystorePassword().toCharArray(),
					configuration.getIntegrations().getNemLogin().getKeystorePassword().toCharArray())
				.loadTrustMaterial(acceptingTrustStrategy)
				.build();

			managerBuilder.setSSLSocketFactory(
				SSLConnectionSocketFactoryBuilder.create()
					.setSslContext(sslContext)
					.setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
					.build()
			);
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
		return RequestConfig.custom().build();  // Empty config, timeouts set on requestFactory
	}
}
