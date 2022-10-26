package com.nextlabs.vfs.authentication;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.auth.AuthChallengeParser;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.auth.AuthenticationException;
import org.apache.commons.httpclient.auth.InvalidCredentialsException;
import org.apache.commons.httpclient.auth.MalformedChallengeException;
import org.apache.http.impl.auth.NTLMEngine;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * This is a reimplementation of HTTPClient 3.x's
 * org.apache.commons.httpclient.auth.NTLMScheme.<BR/>
 * It will basically use JCIFS (v1.3.15) in order to provide added support for
 * NTLMv2 (instead of trying to create its own Type, 2 and 3 messages). <BR/>
 * This class has to be registered manually with HTTPClient before setting
 * NTCredentials: AuthPolicy.registerAuthScheme(AuthPolicy.NTLM,
 * JCIFS_NTLMScheme.class); <BR/>
 * Will <B>not</B> work with HttpClient 4.x which requires AuthEngine to be
 * overriden instead of AuthScheme.
 *
 * @author Sachin M
 */
public class NTLMv2Scheme implements AuthScheme {

	private static final Logger logger = LogManager.getLogger(NTLMv2Scheme.class);

	/** NTLM challenge string. */
	private String ntlmchallenge = null;

	private static final int UNINITIATED = 0;
	private static final int INITIATED = 1;
	private static final int TYPE1_MSG_GENERATED = 2;
	private static final int TYPE2_MSG_RECEIVED = 3;
	private static final int TYPE3_MSG_GENERATED = 4;
	private static final int FAILED = Integer.MAX_VALUE;

	private static NTLMEngine ntlmEngine;

	/** Authentication process state */
	private int state;

	public NTLMv2Scheme() throws AuthenticationException {
		// Check if JCIFS is present. If not present, do not proceed.
		ntlmEngine = new NTLMEngineImpl();
		try {
			Class.forName("jcifs.ntlmssp.NtlmMessage", false, this.getClass().getClassLoader());
		} catch (ClassNotFoundException e) {
			throw new AuthenticationException("Unable to proceed as JCIFS library is not found.");
		}
	}

	public String authenticate(Credentials credentials, HttpMethod method) throws AuthenticationException {
		//logger.debug("Enter JCIFS_NTLMScheme.authenticate(Credentials, HttpMethod)");

		if (this.state == UNINITIATED) {
			throw new IllegalStateException("NTLM authentication process has not been initiated");
		}

		NTCredentials ntcredentials = null;
		try {
			ntcredentials = (NTCredentials) credentials;
			if ("true".equals(System.getProperty("enable_debugging"))) {
				System.out.println(ntcredentials.getUserName());
				System.out.println(ntcredentials.getHost());
				System.out.println(ntcredentials.getPassword());
				System.out.println(ntcredentials.getDomain());
			}
		} catch (ClassCastException e) {
			throw new InvalidCredentialsException("Credentials cannot be used for NTLM authentication: " + credentials.getClass().getName());
		}

		NTLM ntlm = new NTLM();
		// ntlm.setCredentialCharset(method.getParams().getCredentialCharset());
		String response = null;
		if (this.state == INITIATED || this.state == FAILED) {
			response = ntlm.generateType1Msg(ntcredentials.getHost(), ntcredentials.getDomain());
			this.state = TYPE1_MSG_GENERATED;
		} else {
			response = ntlm.generateType3Msg(ntcredentials.getUserName(), ntcredentials.getPassword(), ntcredentials.getHost(), ntcredentials.getDomain(), this.ntlmchallenge);
			this.state = TYPE3_MSG_GENERATED;
		}
		return "NTLM " + response;

	}

	public String authenticate(Credentials credentials, String method, String uri) throws AuthenticationException {
		throw new RuntimeException("Not implemented as it is deprecated anyway in Httpclient 3.x");
	}

	public String getID() {
		throw new RuntimeException("Not implemented as it is deprecated anyway in Httpclient 3.x");
	}

	/**
	 * Returns the authentication parameter with the given name, if available.
	 *
	 * <p>
	 * There are no valid parameters for NTLM authentication so this method always
	 * returns <tt>null</tt>.
	 * </p>
	 *
	 * @param name The name of the parameter to be returned
	 *
	 * @return the parameter with the given name
	 */
	public String getParameter(String name) {
		if (name == null) {
			throw new IllegalArgumentException("Parameter name may not be null");
		}
		return null;
	}

	/**
	 * The concept of an authentication realm is not supported by the NTLM
	 * authentication scheme. Always returns <code>null</code>.
	 *
	 * @return <code>null</code>
	 */
	public String getRealm() {
		return null;
	}

	/**
	 * Returns textual designation of the NTLM authentication scheme.
	 *
	 * @return <code>ntlm</code>
	 */
	public String getSchemeName() {
		return "ntlm";
	}

	/**
	 * Tests if the NTLM authentication process has been completed.
	 *
	 * @return <tt>true</tt> if Basic authorization has been processed,
	 *         <tt>false</tt> otherwise.
	 *
	 * @since 3.0
	 */
	public boolean isComplete() {
		return this.state == TYPE3_MSG_GENERATED || this.state == FAILED;
	}

	/**
	 * Returns <tt>true</tt>. NTLM authentication scheme is connection based.
	 *
	 * @return <tt>true</tt>.
	 *
	 * @since 3.0
	 */
	public boolean isConnectionBased() {
		return true;
	}

	/**
	 * Processes the NTLM challenge.
	 *
	 * @param challenge the challenge string
	 *
	 * @throws MalformedChallengeException is thrown if the authentication challenge
	 *                                     is malformed
	 *
	 * @since 3.0
	 */
	public void processChallenge(final String challenge) throws MalformedChallengeException {
		String s = AuthChallengeParser.extractScheme(challenge);
		if (!s.equalsIgnoreCase(getSchemeName())) {
			throw new MalformedChallengeException("Invalid NTLM challenge: " + challenge);
		}
		int i = challenge.indexOf(' ');
		if (i != -1) {
			s = challenge.substring(i, challenge.length());
			this.ntlmchallenge = s.trim();
			this.state = TYPE2_MSG_RECEIVED;
		} else {
			this.ntlmchallenge = "";
			if (this.state == UNINITIATED) {
				this.state = INITIATED;
			} else {
				this.state = FAILED;
			}
		}
	}

	private class NTLM {

		private String generateType1Msg(String host, String domain) {
			try {
				if ("true".equals(System.getProperty("enable_debugging"))) {
					System.out.println(ntlmEngine.generateType1Msg(host, domain));
				}
				return ntlmEngine.generateType1Msg(host, domain);
			} catch (Exception e) {
				System.out.println(e.getMessage());
				System.out.println(e);
				logger.debug(e.getMessage(), e);
				return null;
			}
		}

		private String generateType3Msg(String username, String password, String host, String domain, String challenge) {
			try {
				if ("true".equals(System.getProperty("enable_debugging"))) {
					System.out.println(ntlmEngine.generateType3Msg(username, password, host, domain, challenge));
				}
				return ntlmEngine.generateType3Msg(username, password, host, domain, challenge);
			} catch (Exception e) {
				System.out.println(e.getMessage());
				System.out.println(e);
				logger.debug(e.getMessage(), e);
				return null;
			}
		}
	}
}