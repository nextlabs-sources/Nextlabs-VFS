package com.nextlabs.vfs.authentication;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

//The purpose of this class is to intercept outgoing HTTP requests to the indexer server
//and preemptively add a basic authentication header to them when required. It is used
//exclusively by com.nextlabs.smartclassifier.solr.SolrHttpcClientFactory

public class PreemptiveBasicAuthInterceptor implements HttpRequestInterceptor {

	private final CredentialsProvider credProvider;

	public PreemptiveBasicAuthInterceptor(CredentialsProvider credProvider) {
		this.credProvider = credProvider;
	}

	@Override
	public void process(HttpRequest request, HttpContext context) throws HttpException {
		AuthState authState = (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);
		if (authState.getAuthScheme() == null) {
			CredentialsProvider credsProvider = this.credProvider;
			HttpHost targetHost = (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);
			Credentials credentials = credsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()));
			if (credentials == null) {
				throw new HttpException("No credentials provided for preemptive authentication.");
			}
			authState.update(new BasicScheme(), credentials);
		}
	}
}
