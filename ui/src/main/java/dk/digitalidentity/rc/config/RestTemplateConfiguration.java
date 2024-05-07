package dk.digitalidentity.rc.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Iterator;

@Configuration
public class RestTemplateConfiguration {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Bean(name = "defaultRestTemplate")
	public RestTemplate defaultRestTemplate() {
		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();

		final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(3 * 60 * 1000);
		requestFactory.setConnectTimeout(3 * 60 * 1000);
		managerBuilder.setDefaultSocketConfig(
				SocketConfig.custom().setSoTimeout(Timeout.ofMinutes(3)).build()
		);

		requestFactory.setHttpClient(
				HttpClients.custom()
						.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
						.setConnectionManager(managerBuilder.build())
						.build()
		);
		final RestTemplate restTemplate = new RestTemplate(requestFactory);
		
		// TODO: not needed once we move to our new KLE master (STS Klassifikation)
		// remove jackson XML converter, as it does not play well with KLE XML
		for (Iterator<HttpMessageConverter<?>> iterator = restTemplate.getMessageConverters().iterator(); iterator.hasNext();) {
			HttpMessageConverter<?> converter = iterator.next();
			
			if (converter.getClass().equals(MappingJackson2XmlHttpMessageConverter.class)) {
				iterator.remove();
			}
		}
		
		// add the jackson XML converter, which DOES play well with KLE XML
		restTemplate.getMessageConverters().add(new Jaxb2RootElementHttpMessageConverter());
		
		return restTemplate;
	}
	
	@Bean(name = "kspCicsRestTemplate")
	public RestTemplate kspCicsRestTemplate() throws Exception {
		final TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();
		if (configuration.getIntegrations().getKspcics().isEnabled()) {
			final SSLContext sslContext = SSLContextBuilder.create()
			                .loadKeyMaterial(
			                		ResourceUtils.getFile(configuration.getIntegrations().getKspcics().getKeystoreLocation()),
			                		configuration.getIntegrations().getKspcics().getKeystorePassword().toCharArray(),
			                		configuration.getIntegrations().getKspcics().getKeystorePassword().toCharArray())
			                .loadTrustMaterial(acceptingTrustStrategy)
			                .build();
			managerBuilder.setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
					.setSslContext(sslContext)
					.build());
		}

		final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(3 * 60 * 1000);
		requestFactory.setConnectTimeout(3 * 60 * 1000);
		managerBuilder.setDefaultSocketConfig(
				SocketConfig.custom().setSoTimeout(Timeout.ofMinutes(3)).build()
		);
		final CloseableHttpClient client = HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
				.setConnectionManager(managerBuilder.build())
				.build();
		requestFactory.setHttpClient(client);

		final RestTemplate restTemplate = new RestTemplate(requestFactory);
		restTemplate.setErrorHandler(new ResponseErrorHandler() {
			
			@Override
			public boolean hasError(ClientHttpResponse response) throws IOException {
				return false;
			}
			
			@Override
			public void handleError(ClientHttpResponse response) throws IOException {
				;
			}
		});

		return restTemplate;
	}
	
	@Bean(name = "kombitRestTemplate")
	public RestTemplate kombitRestTemplate() throws Exception {
		final TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();

		if (configuration.getIntegrations().getKombit().isEnabled() && StringUtils.hasLength(configuration.getIntegrations().getKombit().getKeystoreLocation())) {
			managerBuilder.setSSLSocketFactory(kombitSocketFactory(acceptingTrustStrategy));
		}
		final CloseableHttpClient client = HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
				.setConnectionManager(managerBuilder.build())
				.build();
		final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(3 * 60 * 1000);
		requestFactory.setConnectTimeout(3 * 60 * 1000);
		managerBuilder.setDefaultSocketConfig(
				SocketConfig.custom().setSoTimeout(Timeout.ofMinutes(3)).build()
		);
		requestFactory.setHttpClient(client);

		final RestTemplate restTemplate = new RestTemplate(requestFactory);
		/* NOPE, not until we rewrite the usage to deal with statuscodes instead
		restTemplate.setErrorHandler(new ResponseErrorHandler() {
			
			@Override
			public boolean hasError(ClientHttpResponse response) throws IOException {
				return false;
			}
			
			@Override
			public void handleError(ClientHttpResponse response) throws IOException {
				;
			}
		});
		*/

		return restTemplate;
	}
	
	
	@Bean(name = "kombitTestRestTemplate")
	public RestTemplate kombitTestRestTemplate() throws Exception {
		final TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();
		if (configuration.getIntegrations().getKombit().isTestEnabled() && StringUtils.hasLength(configuration.getIntegrations().getKombit().getTestKeystoreLocation())) {
			managerBuilder.setSSLSocketFactory(kombitSocketFactory(acceptingTrustStrategy));
		}

		final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(3 * 60 * 1000);
		requestFactory.setConnectTimeout(3 * 60 * 1000);
		managerBuilder.setDefaultSocketConfig(
				SocketConfig.custom().setSoTimeout(Timeout.ofMinutes(3)).build()
		);
		requestFactory.setHttpClient(HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
				.setConnectionManager(managerBuilder.build())
				.build());

		final RestTemplate restTemplate = new RestTemplate(requestFactory);
		/* NOPE, not until we rewrite the usage to deal with statuscodes instead
		restTemplate.setErrorHandler(new ResponseErrorHandler() {
			
			@Override
			public boolean hasError(ClientHttpResponse response) throws IOException {
				return false;
			}
			
			@Override
			public void handleError(ClientHttpResponse response) throws IOException {
				;
			}
		});
		*/

		return restTemplate;
	}

	@Bean(name = "nemLoginRestTemplate")
	public RestTemplate nemLoginRestTemplate() throws Exception {
		final TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

		final PoolingHttpClientConnectionManagerBuilder managerBuilder = PoolingHttpClientConnectionManagerBuilder.create();
		if (configuration.getIntegrations().getNemLogin().isEnabled() && StringUtils.hasLength(configuration.getIntegrations().getNemLogin().getKeystoreLocation()) && StringUtils.hasLength(configuration.getIntegrations().getNemLogin().getKeystorePassword())) {
			final SSLContext sslContext = SSLContextBuilder.create()
			                .loadKeyMaterial(
			                		ResourceUtils.getFile(configuration.getIntegrations().getNemLogin().getKeystoreLocation()),
			                		configuration.getIntegrations().getNemLogin().getKeystorePassword().toCharArray(),
			                		configuration.getIntegrations().getNemLogin().getKeystorePassword().toCharArray())
			                .loadTrustMaterial(acceptingTrustStrategy)
			                .build();
			managerBuilder.setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
					.setSslContext(sslContext)
					.build());
		}
		final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(3 * 60 * 1000);
		requestFactory.setConnectTimeout(3 * 60 * 1000);
		managerBuilder.setDefaultSocketConfig(
				SocketConfig.custom().setSoTimeout(Timeout.ofMinutes(3)).build()
		);
		requestFactory.setHttpClient(HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
				.setConnectionManager(managerBuilder.build())
				.build());
		final RestTemplate restTemplate = new RestTemplate(requestFactory);
		restTemplate.setErrorHandler(new ResponseErrorHandler() {
			
			@Override
			public boolean hasError(ClientHttpResponse response) throws IOException {
				return false;
			}
			
			@Override
			public void handleError(ClientHttpResponse response) throws IOException {
			}
		});
		return restTemplate;
	}


	private LayeredConnectionSocketFactory kombitSocketFactory(TrustStrategy acceptingTrustStrategy) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException, CertificateException, IOException {
		final SSLContext sslContext = SSLContextBuilder.create()
				.loadKeyMaterial(
						ResourceUtils.getFile(configuration.getIntegrations().getKombit().getTestKeystoreLocation()),
						configuration.getIntegrations().getKombit().getTestKeystorePassword().toCharArray(),
						configuration.getIntegrations().getKombit().getTestKeystorePassword().toCharArray())
				.loadTrustMaterial(acceptingTrustStrategy)
				.build();
        return SSLConnectionSocketFactoryBuilder.create()
				.setCiphers("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
				.setSslContext(sslContext)
				.setHostnameVerifier(new DefaultHostnameVerifier())
				.setTlsVersions(TLS.V_1_2, TLS.V_1_3)
				.build();
	}
}
