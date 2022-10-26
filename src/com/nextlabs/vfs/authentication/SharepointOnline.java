package com.nextlabs.vfs.authentication;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.CookieHandler;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.xml.sax.SAXException;

public class SharepointOnline {
	public static Pair<String, String> getCookies(String domain, String username, String password) {
		username = StringEscapeUtils.escapeXml11(username);
		password = StringEscapeUtils.escapeXml11(password);
		Pair<String, String> result;
		String token;
		try {
			token = AzureAD.getAuthToken(domain, username, password);
			if (token == null) {
				return null;
			}
			result = submitToken(domain, token);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private static Pair<String, String> submitToken(String domain, String token) throws IOException {
		String loginContextPath = "/_forms/default.aspx?wa=wsignin1.0";
		String url = String.format("https://%s.sharepoint.com%s", domain, loginContextPath);
		CookieHandler.setDefault(null);
		URL u = new URL(url);
		URLConnection uc = u.openConnection();
		HttpURLConnection connection = (HttpURLConnection) uc;
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setRequestMethod("POST");
		connection.addRequestProperty("Accept", "application/x-www-form-urlencoded");
		connection.addRequestProperty("Content-Type", "text/xml; charset=utf-8");
		connection.setInstanceFollowRedirects(false);
		OutputStream out = connection.getOutputStream();
		Writer writer = new OutputStreamWriter(out);
		writer.write(token);
		writer.flush();
		out.flush();
		writer.close();
		out.close();

		String rtFa = null;
		String fedAuth = null;
		Map<String, List<String>> headerFields = connection.getHeaderFields();
		List<String> cookiesHeader = headerFields.get("Set-Cookie");
		if (cookiesHeader != null) {
			for (String cookie : cookiesHeader) {
				if (cookie.startsWith("rtFa=")) {
					rtFa = HttpCookie.parse(cookie).get(0).getValue();
				} else if (cookie.startsWith("FedAuth=")) {
					fedAuth = HttpCookie.parse(cookie).get(0).getValue();
				} else {
					// logger.info("waste=" + HttpCookie.parse(cookie).get(0).getValue());
				}
			}
		}
		Pair<String, String> result = ImmutablePair.of(rtFa, fedAuth);
		return result;
	}
}
