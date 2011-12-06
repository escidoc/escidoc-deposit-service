/**
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at license/ESCIDOC.LICENSE
 * or https://www.escidoc.org/license/ESCIDOC.LICENSE .
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
 *
 *
 *
 * Copyright 2011 Fachinformationszentrum Karlsruhe Gesellschaft
 * fuer wissenschaftlich-technische Information mbH and Max-Planck-
 * Gesellschaft zur Foerderung der Wissenschaft e.V.
 * All rights reserved.  Use is subject to license terms.
 */
/*
 * Copyright 2006-2008 Fachinformationszentrum Karlsruhe Gesellschaft
 * fuer wissenschaftlich-technische Information mbH and Max-Planck-
 * Gesellschaft zur Foerderung der Wissenschaft e.V.  
 * All rights reserved.  Use is subject to license terms.
 */
package de.escidoc.bwelabs.depositor.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.escidoc.bwelabs.deposit.Configuration;
import de.escidoc.bwelabs.depositor.error.AlreadyExistException;
import de.escidoc.bwelabs.depositor.error.AlreadyExpiredException;
import de.escidoc.bwelabs.depositor.error.ApplicationException;
import de.escidoc.bwelabs.depositor.error.DepositorException;
import de.escidoc.bwelabs.depositor.error.WrongConfigurationContentException;

/**
 * Handles requests to Depositor service.
 * 
 * @author ROF
 * 
 */
public class DepositorServlet extends HttpServlet {

    public static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";

    public static final String ESCIDOC_CHECKSUM_HEADER = "X-ESciDoc-CheckSum";

    private static final long serialVersionUID = -2846807557758308527L;

    public static final String PATH_FOR_SENDING_NEW_CONFIGURATION = "/configuration";

    public static final String START_PATH_FOR_SENDING_START_COMMAND = "/esync/configuration/start/";

    public static final String START_PATH_FOR_SENDING_STOP_COMMAND = "/esync/configuration/";

    public static final String HTTP_PUT = "PUT";

    public static final String HTTP_POST = "POST";

    public static final String HTTP_DELETE = "DELETE";

    public static final String HTTP_HEAD = "HEAD";

    public static final String HTTP_GET = "GET";

    public static final String INIT_PARAM_SERVER_NAME = "server-name";

    public static final String INIT_PARAM_PORT = "port";

    public static final String INIT_PARAM_CONTEXT_PATH = "context-path";

    public static final String DEFAULT_PORT = "80";

    private static final Logger LOGGER = LoggerFactory.getLogger(DepositorServlet.class.getName());

    private SAXParserFactory saxParserFactory;

    private SessionManager manager;

    private String contentFileServletUrl;

    /**
     * Method calls a SessionManager.storeConfiguration() method to check and register the configuration, provided in
     * the body of the request.
     */
    @Override
    public void doPut(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        LOGGER.debug("PUT " + pathInfo);

        if (!PATH_FOR_SENDING_NEW_CONFIGURATION.equals(pathInfo)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "A PUT-requst to path info: " + pathInfo
                + " is not suported.");
        }

        try {

            InputStream is = getRequestInputStream(request, response);
            Configuration configProperties = new Configuration();
            configProperties.loadFromXML(is);
            configProperties.isValid();
            String configId = configProperties.getProperty(Configuration.PROPERTY_CONFIGURATION_ID);
            manager.checkIfAlreadyExists(configId);

            File configFile = manager.saveInLocalFileSystem(configProperties);

            manager.ingestConfiguration(configProperties, configFile);

            manager.registerConfiguration(configProperties);

            LOGGER.info("Successfully saved configuration: " + configId + " in " + configFile.getPath() + ".");

            // FIXME respond without content
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/xml");
            response.flushBuffer();
        }
        catch (InvalidPropertiesFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getClass().getName() + ": " + e.getMessage());
        }
        catch (WrongConfigurationContentException e) {
            response.sendError(HttpServletResponse.SC_CONFLICT, e.getClass().getName() + ": " + e.getMessage());
        }
        catch (AlreadyExistException e) {
            response.sendError(HttpServletResponse.SC_CONFLICT, e.getClass().getName() + ": " + e.getMessage());
        }
        catch (DepositorException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                e.getClass().getName() + ": " + e.getMessage());
        }
        // TODO needed to inform eSyncDaemon about no connection to infrastructure?
        // catch (ConnectionException e) {
        // response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
        // }
        catch (IOException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Method calls a SessionManager.checkCheckSum() method to store a content, provided in the body of the request,
     * into an infrastructure.
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // FIXME Preconditions are:
        // http://{host:port}/deposit-service/depositor/{configId}
        // configId can not be null or empty
        // TODO
        // String configId = request.getPathInfo().substring(1);

        // String checkSumValue = request.getHeader(ESCIDOC_CHECKSUM_HEADER);
        // String fileName = request.getHeader(CONTENT_DISPOSITION);

        LOGGER.debug("POST");
        InputStream is = getRequestInputStream(request, response);
        checkSum(request, response, is);
    }

    private static void sendError(HttpServletResponse response) {
        String message = "A stream with the content file is null";
        LOGGER.error(message);
        try {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, message);
        }
        catch (IOException ioe) {
            LOGGER.warn("Could not send error to eSyncDemon", ioe);
        }
    }

    private static InputStream getRequestInputStream(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        InputStream is = null;
        is = request.getInputStream();
        if (is == null) {
            // FIXME method states to return an object; not "or null"!
            String message = "Configuration stream is null";
            LOGGER.error(message);
            throw new IOException(message);
        }
        return is;
    }

    private void checkSum(HttpServletRequest request, HttpServletResponse response, InputStream is) {
        try {
            String checkSumValue = request.getHeader(ESCIDOC_CHECKSUM_HEADER);
            String fileName = request.getHeader(CONTENT_DISPOSITION_HEADER);
            String configId = request.getPathInfo().substring(1);

            // FIXME check for empty value OR null and react accordingly
            // TODO what does this line do?
            manager.increaseThreadsNumber();

            if (isValid(is, checkSumValue, fileName, configId)) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/xml");
                response.flushBuffer();
            }
            else {
                sendInvalidChecksum(response);
            }
        }
        catch (ApplicationException e) {
            handleApplicationException(response, e);
        }
        catch (DepositorException e) {
            sendDepositError(response, e);
        }
        catch (Throwable e) {
            sendOtherError(response, e);
        }
        finally {
            cleanUp(is);
        }
    }

    private void cleanUp(InputStream is) {
        manager.decreaseThreadsNumber();
        try {
            is.close();
        }
        catch (IOException e) {
            LOGGER.warn("Error closing input stream", e);
        }
    }

    private static void sendOtherError(HttpServletResponse response, Throwable e) {
        try {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
        catch (IOException ioe) {
            LOGGER.warn("Could not send error to eSyncDemon", ioe);
        }
    }

    private static void sendDepositError(HttpServletResponse response, DepositorException e) {
        try {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
        catch (IOException ioe) {
            LOGGER.warn("Could not send error to eSyncDemon", ioe);
        }
    }

    private static void handleApplicationException(HttpServletResponse response, ApplicationException e) {
        if ((e instanceof AlreadyExistException) || (e instanceof AlreadyExpiredException)) {
            try {
                response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
            }
            catch (IOException ioe) {
                LOGGER.warn("Could not send error to eSyncDemon", ioe);
            }
        }
        else {
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            }
            catch (IOException ioe) {
                LOGGER.warn("Could not send error to eSyncDemon", ioe);
            }
        }
    }

    private static void sendInvalidChecksum(HttpServletResponse response) {
        try {
            response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
                "A calculated check sum of the attached file " + "does not match the provided check sum.");
        }
        catch (IOException ioe) {
            LOGGER.warn("Could not send error to eSyncDemon", ioe);
        }
    }

    private boolean isValid(InputStream is, String checkSumValue, String fileName, String configId)
        throws ApplicationException, DepositorException {
        return manager.refactorNameOfThisMethod(configId, checkSumValue, is, fileName);
    }

    /**
     * Method calls a SessionManager.deleteConfiguration() method to delete a data for the configuration with id,
     * provided in a request-url from a Depositor service and file system.
     */
    @Override
    public void doDelete(HttpServletRequest request, HttpServletResponse response) {
        LOGGER.debug("DELETE");
        String pathInfo = request.getPathInfo();
        String configId = pathInfo.substring(1);
        try {
            manager.increaseThreadsNumber();
            manager.deleteConfiguration(configId);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/xml");
            // response.flushBuffer();
        }
        catch (ApplicationException e) {
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            }
            catch (IOException ioe) {
                LOGGER.warn("Could not send error to eSyncDemon", ioe);
            }

        }
        catch (DepositorException e) {
            sendDepositError(response, e);

        }
        catch (Throwable e) {
            sendOtherError(response, e);
        }
        finally {
            manager.decreaseThreadsNumber();
        }

    }

    @Override
    public void init() throws ServletException {
        try {
            // prepare parser factory for later use
            // only used for parsing web.xml to get servlet context
            // this.saxParserFactory = SAXParserFactory.newInstance();
            // this.saxParserFactory.setValidating(false);
            // this.saxParserFactory.setNamespaceAware(true);

            // from here it seems a base-url for files in this service is build
            String serverName = getServletContext().getInitParameter(INIT_PARAM_SERVER_NAME);
            String port = getServletContext().getInitParameter(INIT_PARAM_PORT);
            String contextPath = getServletContext().getInitParameter(INIT_PARAM_CONTEXT_PATH);
            String servletPath = getContentFileServletPath();
            if (port == null) {
                port = DEFAULT_PORT;
            }
            if ((serverName == null) || (contextPath == null)) {
                String message =
                    "Unable to initialize DepositorServlet: one or more of context parameters: "
                        + INIT_PARAM_CONTEXT_PATH + " , " + INIT_PARAM_SERVER_NAME + " is not set in the web.xml file.";
                LOGGER.error(message);
                throw new ServletException(message);
            }
            // build a request-url for a ContentFileServlet. ContentFileServlet
            // makes content
            // files, stored in a file system of a Depositor service, accessible
            // for extern services.
            this.contentFileServletUrl = "http://" + serverName + ":" + port + "/" + contextPath + "/" + servletPath;

            // load configuration
            Properties props = loadConfiguration();
            // create session manager that will run as thread creating
            // additional threads for all configurations
            this.manager = new SessionManager(props, this.contentFileServletUrl);
        }
        catch (Exception e) {
            String message = "Unable to initialize DepositorServlet (" + this.contentFileServletUrl + ")";
            LOGGER.error(message, e);
            throw new ServletException(message, e);
        }
    }

    private Properties loadConfiguration() throws IOException {
        InputStream propStream = null;
        try {
            propStream = this.getClass().getResourceAsStream("/depositor.properties");
            if (propStream == null) {
                String message = "Error loading configuration: /depositor.properties not found in classpath";
                throw new IOException(message);
            }
            Properties props = new Properties();
            props.load(propStream);
            return props;
        }
        finally {
            if (propStream != null) {
                propStream.close();
            }

        }
    }

    /**
     * Method parses a web.xml file of a web application to find out a servlet-path for a
     * ContentFileServlet-Request-URL.
     * 
     * @return string with a
     * @throws DepositorException
     */
    private static String getContentFileServletPath() throws DepositorException {
        // FIXME this method should determine url pattern of ContentServlet.
        // needs proxy settings for accessing web.xml doctype dtd.

        // ServletContext context = this.getServletContext();
        // InputStream webXml = context.getResourceAsStream("/WEB-INF/web.xml");
        // SAXParser parser = null;
        //
        // try {
        // parser = saxParserFactory.newSAXParser();
        // }
        // catch (ParserConfigurationException e) {
        // LOGGER.error(e);
        // throw new DepositorException(e.getMessage(), e);
        // }
        // catch (SAXException e) {
        // LOGGER.error(e);
        // throw new DepositorException(e.getMessage(), e);
        // }
        // WebXmlHandler dh =
        // new WebXmlHandler(
        // "de.escidoc.bwelabs.depositor.service.ContentFileServlet");
        // if (webXml != null) {
        // try {
        // parser.parse(webXml, dh);
        // webXml.close();
        // }
        // catch (SAXException e) {
        // LOGGER.error(e);
        // throw new DepositorException(e.getMessage(), e);
        // }
        // catch (IOException e) {
        // LOGGER.error(e);
        // throw new DepositorException(e.getMessage(), e);
        // }
        // }
        // else {
        // String message = "Error getting inut stream with web.xml.";
        // throw new DepositorException(message);
        // }
        // String urlPattern = dh.getUrlPattern();
        // String servletName = dh.getServletName();
        // if ((urlPattern == null) && (servletName == null)) {
        // String message =
        // "A servlet mapping for a ContentFileServlet is"
        // + " not found in a web.xml file of a web application";
        // throw new DepositorException(message);
        // }
        // else if (urlPattern == null) {
        // String message =
        // "A servlet mapping for a " + servletName
        // + " is not found in a web.xml file of a web application";
        // throw new DepositorException(message);
        // }
        // if (urlPattern.startsWith("/")) {
        // urlPattern = urlPattern.substring(1);
        // }
        // if (urlPattern.endsWith("/*")) {
        // urlPattern = urlPattern.substring(0, urlPattern.length() - 2);
        // }
        // else {
        // String message =
        // "The url-pattern in servlet mapping for a "
        // + "ContentFileServlet should ends with '*'";
        // throw new DepositorException(message);
        // }
        // return urlPattern;
        return "content";
    }

    /**
     * Close the Manager at shutdown-time.
     * 
     * This makes a best-effort attempt to properly close threads.
     */
    @Override
    public void destroy() {
        this.manager.close();
    }
}
