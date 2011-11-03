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
package de.escidoc.bwelabs.depositor.saxHandler;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Extracts servlet name and url-pattern of a provided servlet class from a
 * web.xml of a web application.
 * 
 * @author ROF
 * 
 */
public class WebXmlHandler extends DefaultHandler {

    private boolean inServlet = false;

    private boolean inServletMapping = false;

    private boolean inServletNameServlet = false;

    private boolean inServletNameMapping = false;

    private boolean inServletClass = false;

    private boolean inUrlPattern = false;

    private boolean inDesiredMapping = false;

    private String servletClass;

    private String servletNameServlet;

    private String servletClassServlet;

    private String servletNameMapping;

    private String foundNameServlet;

    private String urlPattern;

    private String foundUrlPattern;

    public WebXmlHandler(final String servletClass) {
        this.servletClass = servletClass;
    }

    public void startElement(
        final String uri, final String localName, final String qName,
        final Attributes attributes) {

        if (inServlet) {
            if (localName.equals("servlet-name")) {
                inServletNameServlet = true;
            }
            else if (localName.equals("servlet-class")) {
                inServletClass = true;
            }
        }
        else if (inServletMapping) {
            if (localName.equals("servlet-name")) {
                inServletNameMapping = true;
            }
            else if (localName.equals("url-pattern")) {
                inUrlPattern = true;
            }
        }
        else if (localName.equals("servlet")) {
            inServlet = true;

        }
        else if (localName.equals("servlet-mapping")) {
            inServletMapping = true;

        }

    }

    public void endElement(
        final String uri, final String localName, final String qName) {

        if (inServlet) {
            if (localName.equals("servlet")) {
                inServlet = false;
            }
            else if (inServletNameServlet) {
                inServletNameServlet = false;
            }
            else if (inServletClass) {
                if (this.servletClass.equals(this.servletClassServlet)) {
                    this.foundNameServlet = this.servletNameServlet;
                }
                inServletClass = false;
                this.servletNameServlet = null;
                this.servletClassServlet = null;
            }
        }
        else if (inServletMapping) {
            if (localName.equals("servlet-mapping")) {
                inServletMapping = false;
            }
            else if (inServletNameMapping) {
                if (this.servletNameMapping.equals(this.foundNameServlet)) {
                    inDesiredMapping = true;
                }
                inServletNameMapping = false;
                this.servletNameMapping = null;
            }
            else if (inUrlPattern && inDesiredMapping) {
                this.foundUrlPattern = this.urlPattern;

            }
            inUrlPattern = false;
            this.urlPattern = null;
        }
    }

    public void characters(final char[] ch, final int start, final int length) {

        if (inServletNameServlet) {
            if (this.servletNameServlet == null) {
                this.servletNameServlet = new String(ch, start, length);
            }
            else {
                this.servletNameServlet =
                    this.servletNameServlet + new String(ch, start, length);
            }
        }
        if (inServletClass) {
            if (this.servletClassServlet == null) {
                this.servletClassServlet = new String(ch, start, length);
            }
            else {
                this.servletClassServlet =
                    this.servletClassServlet + new String(ch, start, length);
            }
        }
        if (inServletNameMapping) {
            if (this.servletNameMapping == null) {
                this.servletNameMapping = new String(ch, start, length);
            }
            else {
                this.servletNameMapping =
                    this.servletNameMapping + new String(ch, start, length);
            }
        }

        else if (inUrlPattern) {
            if (this.urlPattern == null) {
                this.urlPattern = new String(ch, start, length);
            }
            else {
                this.urlPattern =
                    this.urlPattern + new String(ch, start, length);
            }
        }
    }

    public String getUrlPattern() {
        return this.foundUrlPattern;
    }

    public String getServletName() {
        return this.foundNameServlet;
    }

}
