/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at license/ESCIDOC.LICENSE
 * or http://www.escidoc.de/license.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at license/ESCIDOC.LICENSE.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright 2006-2008 Fachinformationszentrum Karlsruhe Gesellschaft
 * fuer wissenschaftlich-technische Information mbH and Max-Planck-
 * Gesellschaft zur Foerderung der Wissenschaft e.V.  
 * All rights reserved.  Use is subject to license terms.
 */
package de.escidoc.bwelabs.depositor.utility;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.escidoc.bwelabs.depositor.error.ApplicationException;
import de.escidoc.bwelabs.depositor.error.ConnectionException;
import de.escidoc.bwelabs.depositor.error.DepositorException;
import de.escidoc.bwelabs.depositor.error.InfrastructureException;

/**
 * An utility class for HTTP requests.
 * 
 * @author ROF
 */

public class ConnectionUtility {

	private static final Logger log = LoggerFactory
			.getLogger(ConnectionUtility.class);

	private static final int HTTP_RESPONSE_OK = 200;

	private HttpClient httpClient = null;

	private static final int HTTP_MAX_CONNECTIONS_PER_HOST = 30;

	private static final int HTTP_MAX_TOTAL_CONNECTIONS_FACTOR = 3;

	private MultiThreadedHttpConnectionManager cm = new MultiThreadedHttpConnectionManager();

	/**
	 * Get the HTTP Client (multi threaded).
	 * 
	 * @return HttpClient
	 * 
	 */
	public HttpClient getHttpClient() {
		if (this.httpClient == null) {
			this.cm.getParams().setMaxConnectionsPerHost(
					HostConfiguration.ANY_HOST_CONFIGURATION,
					HTTP_MAX_CONNECTIONS_PER_HOST);
			this.cm.getParams().setMaxTotalConnections(
					HTTP_MAX_CONNECTIONS_PER_HOST
							* HTTP_MAX_TOTAL_CONNECTIONS_FACTOR);
			this.httpClient = new HttpClient(this.cm);
			httpClient.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
					new DefaultHttpMethodRetryHandler());
		}
		return this.httpClient;
	}

	/**
	 * 
	 * Method makes a GET-request to Escidoc-Infrastracture.
	 * 
	 * @param url
	 *            The URL for the HTTP GET method.
	 * @param handle
	 * @return GetMethod
	 * 
	 * @throws ApplicationException
	 *             thrown if a the request failed with an ApplicationException
	 *             of Escidoc Infrastracture
	 * @throws InfrastructureException
	 *             thrown if a the request failed with an SystemException of
	 *             Escidoc Infrastracture
	 * @throws ConnectionException
	 *             if Connection to Escidoc Infrastracture failed
	 */

	public GetMethod getEscidoc(final String url, final String handle)
			throws ApplicationException, InfrastructureException,
			ConnectionException {

		GetMethod get = null;

		try {
			get = new GetMethod(url);
		} catch (Throwable e) {
			String message = "Could not send a GET-request to an infrastructure with the url: "
					+ url + " " + e.getMessage();
			log.error(message);
			throw new ApplicationException(message);
		}
		addEscidocUserHandleCokie(get, handle);
		try {
			int responseCode = getHttpClient().executeMethod(get);
			if ((responseCode / 100) != (HTTP_RESPONSE_OK / 100)) {

				String message = get.getResponseBodyAsString();
				Header header = get.getResponseHeader("eSciDocException");
				if (header != null) {
					String value = header.getValue();
					if (value.endsWith("SystemException")) {
						log.error(message);
						get.releaseConnection();
						throw new InfrastructureException(message);
					} else {
						log.error(message);
						get.releaseConnection();
						throw new ApplicationException(message);
					}
				} else {
					String message1 = "GET request to an infrastructure with the url: "
							+ url + " resultes with an Exception " + message;
					log.error(message1);
					get.releaseConnection();
					throw new ConnectionException(message1);
				}
			}
		} catch (HttpException e) {
			String message = "Could not send a GET-request to an infrastructure with the url: "
					+ url + " " + e.getMessage();
			log.error(message);
			throw new ConnectionException(message, e);
		} catch (IOException e) {
			String message = "Could not send a POST-request to an infrastructure with the url: "
					+ url + " " + e.getMessage();
			log.error(message);
			throw new ConnectionException(message, e);
		}

		return get;
	}

	/**
	 * 
	 * Method makes a POST-request to Escidoc-Infrastracture.
	 * 
	 * @param url
	 *            The URL for the HTTP POST request
	 * @param body
	 *            The body for the POST request.
	 * 
	 * @param handle
	 * @return PostMethod
	 * 
	 * @throws ApplicationException
	 *             thrown if a the request failed with an ApplicationException
	 *             of Escidoc Infrastracture
	 * @throws InfrastructureException
	 *             thrown if a the request failed with an SystemException of
	 *             Escidoc Infrastracture
	 * @throws ConnectionException
	 *             if Connection to Escidoc Infrastracture failed
	 * 
	 * @throws DepositorException
	 *             if anything on Depositor went wrong
	 */

	public PostMethod postEscidoc(final String url, final String body,
			final String handle) throws ApplicationException,
			InfrastructureException, DepositorException, ConnectionException {

		PostMethod post = null;
		RequestEntity entity;
		try {

			entity = new StringRequestEntity(body, "text/xml", "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new DepositorException(e.getMessage(), e);
		}

		try {
			post = new PostMethod(url);
		} catch (Throwable e) {
			String message = "Could not send a POST-request to an infrastructure with the url: "
					+ url + " " + e.getMessage();
			log.error(message);
			throw new ApplicationException(message);
		}
		addEscidocUserHandleCokie(post, handle);
		post.setRequestEntity(entity);
		try {
			int responseCode = getHttpClient().executeMethod(post);
			if ((responseCode / 100) != (HTTP_RESPONSE_OK / 100)) {

				String message = post.getResponseBodyAsString();
				Header header = post.getResponseHeader("eSciDocException");
				if (header != null) {
					String value = header.getValue();
					if (value.endsWith("SystemException")) {
						log.error(message);
						post.releaseConnection();
						throw new InfrastructureException(message);
					} else {
						log.error(message);
						post.releaseConnection();
						throw new ApplicationException(message);
					}
				} else {
					String message1 = "POST request to an infrastructure with the url: "
							+ url + " resultes with an Exception " + message;
					log.error(message1);
					post.releaseConnection();
					throw new ConnectionException(message1);
				}
			}
		} catch (HttpException e) {
			String message = "Could not send a POST-request to an infrastructure with the url: "
					+ url + " " + e.getMessage();
			log.error(message);
			throw new ConnectionException(message, e);
		} catch (IOException e) {
			String message = "Could not send a POST-request to an infrastructure with the url: "
					+ url + " " + e.getMessage();
			log.error(message);
			throw new ConnectionException(message, e);
		}

		return post;
	}

	/**
	 * 
	 * Method makes a DELETE-request to Escidoc-Infrastracture.
	 * 
	 * @param url
	 *            The URL for the HTTP DELETE request
	 * @param body
	 *            The body for the DELETE request.
	 * @param handle
	 * @return DeleteMethod
	 * @throws ApplicationException
	 *             thrown if a the request failed with an ApplicationException
	 *             of Escidoc Infrastracture
	 * @throws InfrastructureException
	 *             thrown if a the request failed with an SystemException of
	 *             Escidoc Infrastracture
	 * @throws ConnectionException
	 *             if Connection to Escidoc Infrastracture failed
	 */

	public void deleteEscidoc(final String url, final String handle)
			throws ApplicationException, InfrastructureException,
			ConnectionException {

		DeleteMethod delete = null;

		try {
			delete = new DeleteMethod(url);
		} catch (Throwable e) {
			String message = "Could not send a DELETE-request to an infrastructure with the url: "
					+ url + " " + e.getMessage();
			log.error(message);
			throw new ApplicationException(message);
		}
		addEscidocUserHandleCokie(delete, handle);
		try {
			int responseCode = getHttpClient().executeMethod(delete);
			if ((responseCode / 100) != (HTTP_RESPONSE_OK / 100)) {

				String message = delete.getResponseBodyAsString();
				Header header = delete.getResponseHeader("eSciDocException");
				if (header != null) {
					String value = header.getValue();
					if (value.endsWith("SystemException")) {
						log.error(message);
						delete.releaseConnection();
						throw new InfrastructureException(message);
					} else {
						log.error(message);
						delete.releaseConnection();
						throw new ApplicationException(message);
					}
				} else {
					String message1 = "DELETE request to an infrastructure with the url: "
							+ url + " resultes with an Exception " + message;
					log.error(message1);
					delete.releaseConnection();
					throw new ConnectionException(message1);
				}
			}
		} catch (HttpException e) {
			String message = "Could not send a DELETE-request to an infrastructure with the url: "
					+ url + " " + e.getMessage();
			log.error(message);
			throw new ConnectionException(message, e);
		} catch (IOException e) {
			String message = "Could not send a DELETE-request to an infrastructure with the url: "
					+ url + " " + e.getMessage();
			log.error(message);
			throw new ConnectionException(message, e);
		}

		delete.releaseConnection();
	}

	/**
	 * 
	 * Method makes a PUT-request to Escidoc-Infrastracture.
	 * 
	 * @param url
	 *            The URL for the HTTP PUT request
	 * @param body
	 *            The body for the PUT request.
	 * 
	 * @param handle
	 * @throws ApplicationException
	 *             thrown if a the request failed with an ApplicationException
	 *             of Escidoc Infrastracture
	 * @throws InfrastructureException
	 *             thrown if a the request failed with an SystemException of
	 *             Escidoc Infrastracture
	 * @throws ConnectionException
	 *             if Connection to Escidoc Infrastracture failed
	 * @throws DepositorException
	 *             if anything on Depositor went wrong
	 * 
	 */

	public PutMethod putEscidoc(final String url, final String body,
			final String handle) throws ApplicationException,
			InfrastructureException, DepositorException, ConnectionException {

		PutMethod put = null;
		RequestEntity entity;
		try {

			entity = new StringRequestEntity(body, "text/xml", "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new DepositorException(e.getMessage(), e);
		}

		try {
			put = new PutMethod(url);
		} catch (Throwable e) {
			String message = "Could not send a PUT-request to an infrastructure with the url: "
					+ url + " " + e.getMessage();
			log.error(message);
			throw new ApplicationException(message);
		}
		addEscidocUserHandleCokie(put, handle);
		put.setRequestEntity(entity);
		try {
			int responseCode = getHttpClient().executeMethod(put);
			if ((responseCode / 100) != (HTTP_RESPONSE_OK / 100)) {

				String message = put.getResponseBodyAsString();
				Header header = put.getResponseHeader("eSciDocException");
				if (header != null) {
					String value = header.getValue();
					if (value.endsWith("SystemException")) {
						log.error(message);
						put.releaseConnection();
						throw new InfrastructureException(message);
					} else {
						log.error(message);
						put.releaseConnection();
						throw new ApplicationException(message);
					}
				} else {
					String message1 = "PUT request to an infrastructure with the url: "
							+ url + " resultes with an Exception " + message;
					log.error(message1);
					put.releaseConnection();
					throw new ConnectionException(message1);
				}
			}
		} catch (HttpException e) {
			String message = "Could not send a PUT-request to an infrastructure with the url: "
					+ url + " " + e.getMessage();
			log.error(message);
			throw new ConnectionException(message, e);
		} catch (IOException e) {
			String message = "Could not send a PUT-request to an infrastructure with the url: "
					+ url + " " + e.getMessage();
			log.error(message);
			throw new ConnectionException(message, e);
		}

		return put;
	}

	/**
	 * Adds cookie escidocCookie storing the eSciDoc user handle as the content
	 * of the cookie escidocCookie to to the provided http method object.<br>
	 * The adding is skipped, if the current user handle is <code>null</code> or
	 * equals to an empty <code>String</code>.
	 * 
	 * @param method
	 *            The http method object to add the cookie to.
	 */
	public static void addEscidocUserHandleCokie(final HttpMethod method,
			String handle) {

		if (handle == null || "".equals(handle)) {
			return;
		}
		method.getParams()
				.setCookiePolicy(
						org.apache.commons.httpclient.cookie.CookiePolicy.IGNORE_COOKIES);
		method.setRequestHeader("Cookie", "escidocCookie=" + handle);
	}
}
