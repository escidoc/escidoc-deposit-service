package de.escidoc.bwelabs.depositor.utility;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import sun.net.www.http.HttpClient;

public class HttpUtil {

    private static final int SC_OK = 200;

    private static final int SC_CREATED = 201;

    private static final int SC_ACCEPTED = 202;

    private static final int SC_NON_AUTHORITATIVE_INFORMATION = 203;

    private static final int SC_NO_CONTENT = 204;

    private static final int SC_RESET_CONTENT = 205;

    private static final int SC_PARTIAL_CONTENT = 206;

    private static final String DEFAULT_CHARSET = "UTF-8";

    private final HttpClient client = new HttpClient(
	    new MultiThreadedHttpConnectionManager());

    private String cookie = null;

    private Credentials credentials = null;

    private GetMethod get(final String url) throws AuthenticationException,
	    IOException {
	GetMethod method = null;

	method = new GetMethod(url);
	setAuthHeader(method);

	int responseCode = client.executeMethod(method);

	if (responseCode == SC_OK) {
	    return method;
	} else {
	    throw new IOException(method.getStatusText());
	}
    }

    public byte[] getAsByteArray(final String url) throws IOException,
	    AuthenticationException {
	byte[] result = null;

	GetMethod entity = null;
	try {
	    entity = this.get(url);
	    result = entity.getResponseBody();
	} finally {
	    if (entity != null) {
		entity.releaseConnection();
	    }
	}
	return result;
    }

    public InputStream getAsStream(final String url) throws IOException,
	    AuthenticationException {
	InputStream result = null;

	GetMethod entity = null;
	try {
	    entity = this.get(url);
	    if (entity != null) {
		result = entity.getResponseBodyAsStream();
	    }
	} finally {
	    if (entity != null) {
		entity.releaseConnection();
	    }
	}
	return result;
    }

    public Reader getAsReader(final String url) throws IOException,
	    AuthenticationException {
	Reader result = null;

	GetMethod entity = null;
	try {
	    entity = this.get(url);
	    if (entity != null) {
		String contentEncoding = entity.getResponseCharSet();
		if (contentEncoding == null
			|| contentEncoding.trim().length() == 0) {
		    throw new IOException(
			    "No content encoding found in response. Try getAsStream()");
		}
		result = new InputStreamReader(
			entity.getResponseBodyAsStream(), contentEncoding);
	    }
	} finally {
	    if (entity != null) {
		entity.releaseConnection();
	    }
	}
	return result;
    }

    public String getAsString(final String url) throws Exception, IOException {
	String result = null;
	GetMethod entity = null;
	try {
	    entity = this.get(url);
	    result = entity.getResponseBodyAsString();
	} finally {
	    if (entity != null) {
		entity.releaseConnection();
	    }
	}
	return result;
    }

    public InputStream post(final String url) throws IOException,
	    AuthenticationException {
	InputStream result = null;
	PostMethod method = null;

	try {
	    method = new PostMethod(url);
	    setAuthHeader(method);

	    int responseCode = client.executeMethod(method);

	    if (responseCode == SC_OK) {
		result = method.getResponseBodyAsStream();
	    } else if (responseCode == SC_NO_CONTENT) {
		result = null;
	    } else {
		throw new IOException(method.getStatusText());
	    }
	} finally {
	    if (method != null) {
		method.releaseConnection();
	    }
	}
	return result;
    }

    public InputStream post(final String url, String content)
	    throws IOException, AuthenticationException {
	return this.post(url, content, "text/plain", DEFAULT_CHARSET);
    }

    public InputStream post(final String url, String content,
	    String contentType, String contentEncoding) throws IOException,
	    AuthenticationException {
	InputStream result = null;
	PostMethod method = null;

	try {
	    method = new PostMethod(url);
	    setAuthHeader(method);
	    RequestEntity reqEntity = new StringRequestEntity(content,
		    contentType, contentEncoding);

	    method.setRequestEntity(reqEntity);

	    int responseCode = client.executeMethod(method);

	    if (responseCode >= SC_OK
		    && responseCode <= SC_NON_AUTHORITATIVE_INFORMATION) {
		// SC_OK, SC_CREATED, SC_ACCEPTED,
		// SC_NON_AUTHORITATIVE_INFORMATION
		result = method.getResponseBodyAsStream();
	    } else if (responseCode == SC_NO_CONTENT
		    || responseCode == SC_RESET_CONTENT) {
		result = null;
	    } else {
		throw new IOException(method.getStatusText());
	    }
	} finally {
	    if (method != null) {
		method.releaseConnection();
	    }
	}
	return result;
    }

    public void put(final String url, String content) throws IOException,
	    AuthenticationException {
	// FIXME PUT Request may have a response
	PutMethod method = null;

	try {
	    method = new PutMethod(url);
	    setAuthHeader(method);
	    // FIXME try to use content type and encoding
	    RequestEntity reqEntity = new StringRequestEntity(content);

	    // reqEntity.setContentType("text/xml");
	    // method.setEntity(reqEntity);

	    int responseCode = client.executeMethod(method);

	    // FIXME SC_NO_CONTENT and other
	    if (responseCode == SC_OK) {
		// HttpEntity entity = response.getEntity();
		//
		// if (entity != null) {
		// entity.consumeContent();
		// }
	    } else {
		throw new IOException(method.getStatusText());
	    }
	} finally {
	    if (method != null) {
		method.releaseConnection();
	    }
	}
    }

    private void setAuthHeader(HttpMethodBase method)
	    throws AuthenticationException {
	// used for HttpClient 4.x. In 3.x there is a auth scope added to the
	// client when setCredentials is called.
	// if (credentials != null) {
	// BasicScheme scheme = new BasicScheme();
	// Header authorizationHeader =
	// scheme.authenticate(credentials, method);
	//
	// method.addHeader(authorizationHeader);
	// }
	if (cookie != null) {
	    method.addRequestHeader("Cookie", cookie);
	}
    }

    public void setCookie(String cookie) {
	this.cookie = cookie;
    }

    public void setCredentials(Credentials credentials)
	    throws MalformedURLException {
	setCredentials(credentials, null);
    }

    public void setCredentials(Credentials credentials, String url)
	    throws MalformedURLException {
	this.credentials = credentials;

	// FIXME support at least realm
	AuthScope authScope = null;
	if (url == null) {
	    authScope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT,
		    AuthScope.ANY_REALM);
	} else {
	    authScope = new AuthScope(new URL(url).getHost(),
		    AuthScope.ANY_PORT, AuthScope.ANY_REALM);
	}

	this.client.getState().setCredentials(authScope, this.credentials);

	// don't wait for auth request
	this.client.getParams().setAuthenticationPreemptive(true);
	// try only BASIC auth; skip to test NTLM and DIGEST
	List<String> authPrefs = new ArrayList<String>(1);
	authPrefs.add(AuthPolicy.BASIC);
	this.client.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY,
		authPrefs);
    }

    public void setESciDocHandle(String handle) {
	if (handle == null || "".equals(handle)) {
	    throw new NullPointerException("Handle must not be null.");
	}
	// TODO ConnectionUtility sets cookie policy. What for?
	// method.getParams().setCookiePolicy(CookiePolicy.IGNORE_COOKIES);
	this.cookie = "escidocCookie=" + handle;
    }
}
