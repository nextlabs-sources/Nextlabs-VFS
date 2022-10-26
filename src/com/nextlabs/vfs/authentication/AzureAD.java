package com.nextlabs.vfs.authentication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class AzureAD {
	public static String getAuthToken(String domain, String username, String password) throws XPathExpressionException, SAXException, ParserConfigurationException, IOException {
		String saml = generateSAML(domain, username, password);
		String sts = "https://login.microsoftonline.com/extSTS.srf";
		URL u = new URL(sts);
		URLConnection uc = u.openConnection();
		HttpURLConnection connection = (HttpURLConnection) uc;

		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setRequestMethod("POST");
		connection.addRequestProperty("Content-Type", "text/xml; charset=utf-8");
		OutputStream out = connection.getOutputStream();
		Writer writer = new OutputStreamWriter(out);
		writer.write(saml);

		writer.flush();
		writer.close();

		InputStream in = connection.getInputStream();
		int c;
		StringBuilder sb = new StringBuilder("");
		while ((c = in.read()) != -1) {
			sb.append((char) (c));
		}
		in.close();
		String result = sb.toString();
		String token = extractToken(result);
		if (token == null || token.equals("")) {
			return null;
		}
		return token;
	}

	private static String generateSAML(String domain, String username, String password) {
		String reqXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:a=\"http://www.w3.org/2005/08/addressing\" xmlns:u=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n"
				+ "   <s:Header>\n" + "      <a:Action s:mustUnderstand=\"1\">http://schemas.xmlsoap.org/ws/2005/02/trust/RST/Issue</a:Action>\n" + "      <a:ReplyTo>\n" + "         <a:Address>http://www.w3.org/2005/08/addressing/anonymous</a:Address>\n" + "      </a:ReplyTo>\n"
				+ "      <a:To s:mustUnderstand=\"1\">https://login.microsoftonline.com/extSTS.srf</a:To>\n" + "      <o:Security xmlns:o=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" s:mustUnderstand=\"1\">\n" + "         <o:UsernameToken>\n"
				+ "            <o:Username>[[username]]</o:Username>\n" + "            <o:Password>[[password]]</o:Password>\n" + "         </o:UsernameToken>\n" + "      </o:Security>\n" + "   </s:Header>\n" + "   <s:Body>\n"
				+ "      <t:RequestSecurityToken xmlns:t=\"http://schemas.xmlsoap.org/ws/2005/02/trust\">\n" + "         <wsp:AppliesTo xmlns:wsp=\"http://schemas.xmlsoap.org/ws/2004/09/policy\">\n" + "            <a:EndpointReference>\n" + "               <a:Address>[[endpoint]]</a:Address>\n"
				+ "            </a:EndpointReference>\n" + "         </wsp:AppliesTo>\n" + "         <t:KeyType>http://schemas.xmlsoap.org/ws/2005/05/identity/NoProofKey</t:KeyType>\n" + "         <t:RequestType>http://schemas.xmlsoap.org/ws/2005/02/trust/Issue</t:RequestType>\n"
				+ "         <t:TokenType>urn:oasis:names:tc:SAML:1.0:assertion</t:TokenType>\n" + "      </t:RequestSecurityToken>\n" + "   </s:Body>\n" + "</s:Envelope>";
		String saml = reqXML.replace("[[username]]", username);
		saml = saml.replace("[[password]]", password);
		saml = saml.replace("[[endpoint]]", String.format("https://%s.sharepoint.com/_forms/default.aspx?wa=wsignin1.0", domain));
		return saml;
	}

	private static String extractToken(String result) throws SAXException, IOException, ParserConfigurationException, XPathExpressionException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document document = db.parse(new InputSource(new StringReader(result)));
		XPathFactory xpf = XPathFactory.newInstance();
		XPath xp = xpf.newXPath();
		String token = xp.evaluate("//BinarySecurityToken/text()", document.getDocumentElement());
		return token;
	}
}
