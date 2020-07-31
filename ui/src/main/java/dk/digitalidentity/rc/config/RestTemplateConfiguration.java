package dk.digitalidentity.rc.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

// TODO: Since Spring 5 the Netty implementation has been deprecated, but we
// need it to access the APIs on the Serviceplatform, as Systematics hosting
// setup is not compatible with the build-in Http clients in Spring.
// Once IBM takes over, we can hope we can replace this with the build-in
// HTTP client
@SuppressWarnings("deprecation")
@Configuration
public class RestTemplateConfiguration {

	@Value("${kombit.enabled:false}")
	private boolean kombitEnabled;
	
	@Value("${kombit.keystore.location}")
	private String kombitKeystoreLocation;

	@Value("${kombit.keystore.password}")
	private String kombitKeystorePassword;
	
	@Value("${kmd.kspcics.enabled:false}")
	private boolean kspCicsEnabled;
	
	@Value("${kmd.kspcics.keystore.location}")
	private String kspCicsKeystoreLocation;

	@Value("${kmd.kspcics.keystore.password}")
	private String kspCicsKeystorePassword;

	@Bean(name = "defaultRestTemplate")
	public RestTemplate defaultRestTemplate(RestTemplateBuilder builder) {
		RestTemplate restTemplate = builder.build();
		
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
		if (kspCicsEnabled) {
			SSLContext sslContext = SSLContextBuilder.create()
			                .loadKeyMaterial(ResourceUtils.getFile(kspCicsKeystoreLocation), kspCicsKeystorePassword.toCharArray(), kspCicsKeystorePassword.toCharArray())
			                .loadTrustMaterial(acceptingTrustStrategy)
			                .build();
			
			client = HttpClients.custom().setSSLContext(sslContext).build();
		}
		else {
			client = HttpClients.custom().build();
		}

		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
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
	public RestTemplate kombitRestTemplate(RestTemplateBuilder builder) throws Exception {
		SslContext sslContext = SslContextBuilder.forClient().build();

		if (kombitEnabled && !StringUtils.isEmpty(kombitKeystoreLocation)) {
			KeyStore ks = keyStore(kombitKeystoreLocation, kombitKeystorePassword.toCharArray());
			String alias = ks.aliases().nextElement();
			PrivateKey privKey = (PrivateKey) ks.getKey(alias, kombitKeystorePassword.toCharArray());
			X509Certificate certificate = (X509Certificate) ks.getCertificate(alias);
	
			// build SSL Context containing client certificate
			sslContext = SslContextBuilder.forClient().keyManager(privKey, certificate).build();
		}

		final SslContext context = sslContext;

		Supplier<ClientHttpRequestFactory> supplier = () -> {
			Netty4ClientHttpRequestFactory factory = new Netty4ClientHttpRequestFactory();
			factory.setSslContext(context);
			
			// avoid connection hang
			factory.setConnectTimeout(60000);
			factory.setReadTimeout(60000);

			return factory;
		};

		return builder
				.requestFactory(supplier)
				.build();
	}

	private KeyStore keyStore(String file, char[] password) throws Exception {
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		File key = ResourceUtils.getFile(file);

		try (InputStream in = new FileInputStream(key)) {
			keyStore.load(in, password);
		}

		return keyStore;
	}
}
