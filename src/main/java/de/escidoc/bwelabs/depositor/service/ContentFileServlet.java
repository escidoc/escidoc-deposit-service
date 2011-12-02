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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.rmi.ServerException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles HTTP-GET requests and provides content files, stored in a base directory of the Depositor-service.
 * 
 * @author ROF
 * 
 */
public class ContentFileServlet extends HttpServlet {

    private static final long serialVersionUID = 7215459741116033831L;

    /**
     * Buffer size for copying binary content into output stream.
     */
    private static final int BUFFER_SIZE = 0xFFFF;

    public static final String PROP_BASEDIR = "depositor.sessionBaseDir";

    private String _baseUrl;

    public static final String HTTP_GET = "GET";

    private static final Logger logger = LoggerFactory.getLogger(ContentFileServlet.class.getName());

    /**
     * Method returns content files, stored in a base-directory of a Depositor service.
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) {
        try {
            // extract relative path to content file from request ...
            String pathInfo = request.getPathInfo();
            // ... and access requested content file
            File contentFile = new File(_baseUrl + pathInfo);

            // return content file content
            FileInputStream input = new FileInputStream(contentFile);
            final ServletOutputStream out = response.getOutputStream();
            copyStreams(input, out);
            out.flush();
            out.close();
            input.close();
            response.setStatus(HttpServletResponse.SC_OK);
        }
        catch (FileNotFoundException e) {
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
            catch (IOException ioe) {
                logger.warn("Could not send error", ioe);
            }

        }
        catch (IOException e) {
            logger.error(e.getMessage());
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            catch (IOException ioe) {
                logger.warn("Could not send error", ioe);
            }
        }
    }

    @Override
    public void init() throws ServletException {
        try {
            // load configuration from properties file
            InputStream propStream = this.getClass().getResourceAsStream("/depositor.properties");
            if (propStream == null) {
                throw new IOException("Error loading configuration: /depositor.properties not found in classpath");
            }
            Properties props = new Properties();
            props.load(propStream);

            // set base directory
            String dir = props.getProperty(PROP_BASEDIR);
            if (dir == null) {
                throw new ServerException("Required property missing: " + PROP_BASEDIR);
            }
            _baseUrl = dir;

        }
        catch (Exception e) {
            String message = "Unable to initialize DepositorServlet";
            logger.error(message);
            throw new ServletException(message, e);
        }
    }

    /**
     * Copy InputStream to OutputStream.
     * 
     * @param ins
     *            InputStream
     * @param out
     *            OutputStream
     * @throws IOException
     *             Thrown if copy failed.
     */
    private static void copyStreams(final InputStream ins, final OutputStream out) throws IOException {

        final byte[] buffer = new byte[BUFFER_SIZE];
        int length = 0;
        while ((length = ins.read(buffer)) != -1) {
            out.write(buffer, 0, length);
        }
    }
}
