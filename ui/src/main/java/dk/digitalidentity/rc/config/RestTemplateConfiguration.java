package dk.digitalidentity.rc.config;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Iterator;

import javax.net.ssl.SSLContext;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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

@Configuration
public class RestTemplateConfiguration {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Bean(name = "defaultRestTemplate")
	public RestTemplate defaultRestTemplate() {
		CloseableHttpClient client = HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
				.build();

		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(3 * 60 * 1000);
		requestFactory.setConnectTimeout(3 * 60 * 1000);
		requestFactory.setReadTimeout(3 * 60 * 1000);
		requestFactory.setHttpClient(client);
		
		RestTemplate restTemplate = new RestTemplate(requestFactory);
		
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
		TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

		CloseableHttpClient client = null;
		if (configuration.getIntegrations().getKspcics().isEnabled()) {
			SSLContext sslContext = SSLContextBuilder.create()
			                .loadKeyMaterial(
			                		ResourceUtils.getFile(configuration.getIntegrations().getKspcics().getKeystoreLocation()),
			                		configuration.getIntegrations().getKspcics().getKeystorePassword().toCharArray(),
			                		configuration.getIntegrations().getKspcics().getKeystorePassword().toCharArray())
			                .loadTrustMaterial(acceptingTrustStrategy)
			                .build();
			
			client = HttpClients.custom()
				        .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
						.setSSLContext(sslContext)
						.build();			
		}
		else {
			client = HttpClients.custom()
						.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
						.build();
		}

		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(3 * 60 * 1000);
		requestFactory.setConnectTimeout(3 * 60 * 1000);
		requestFactory.setReadTimeout(3 * 60 * 1000);
		requestFactory.setHttpClient(client);

		RestTemplate restTemplate = new RestTemplate(requestFactory);
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
		TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

		CloseableHttpClient client = null;
		if (configuration.getIntegrations().getKombit().isEnabled() && !StringUtils.isEmpty(configuration.getIntegrations().getKombit().getKeystoreLocation())) {
			SSLContext sslContext = SSLContextBuilder.create()
			                .loadKeyMaterial(
			                		ResourceUtils.getFile(configuration.getIntegrations().getKombit().getKeystoreLocation()),
			                		configuration.getIntegrations().getKombit().getKeystorePassword().toCharArray(),
			                		configuration.getIntegrations().getKombit().getKeystorePassword().toCharArray())
			                .loadTrustMaterial(acceptingTrustStrategy)
			                .build();

			SSLConnectionSocketFactory f = new SSLConnectionSocketFactory(sslContext, 
				    new String[] { "TLSv1.2" },
				    new String[] { "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384" },
				    new DefaultHostnameVerifier());
			
			client = HttpClients.custom()
				        .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
				        .setSSLSocketFactory(f)
						.build();
		}
		else {
			client = HttpClients.custom()
						.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
						.build();
		}

		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setConnectionRequestTimeout(3 * 60 * 1000);
		requestFactory.setConnectTimeout(3 * 60 * 1000);
		requestFactory.setReadTimeout(3 * 60 * 1000);
		
		requestFactory.setHttpClient(client);

		RestTemplate restTemplate = new RestTemplate(requestFactory);
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
}
