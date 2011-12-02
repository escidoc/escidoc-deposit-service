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
package de.escidoc.bwelabs.depositor.saxHandler;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import de.escidoc.bwelabs.depositor.service.Constants;

/**
 * Extracts values of attributes 'item@last-modification-date' 'item@xlink:href', 'properties:checksum',
 * 'component@href' from Escidoc-Item REST-representation.
 * 
 * @author ROF
 * 
 */
public class ItemHandler extends DefaultHandler {
    private boolean inCheckSum = false;

    private String _checkSum = null;

    private String _checkSumForComponent = null;

    private String _lmd = null;

    private String _itemHref = null;

    private String _componentHref = null;

    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        if (localName.equals("item") && uri.equals(Constants.ITEM_NAMESPACE_URI)) {
            this._lmd = attributes.getValue("", "last-modification-date");
            this._itemHref = attributes.getValue(Constants.XLINK_NS_URI, "href");
        }
        else if (localName.equals("checksum") && uri.equals(Constants.PROPERTIES_NS_URI)) {
            inCheckSum = true;
        }
        else if (localName.equals("component") && uri.equals(Constants.COMPONENTS_NAMESPACE_URI)) {
            this._componentHref = attributes.getValue(Constants.XLINK_NS_URI, "href");
        }

    }

    public void endElement(String uri, String localName, String qName) {

        if (localName.equals("checksum") && uri.equals(Constants.PROPERTIES_NS_URI)) {
            inCheckSum = false;
            this._checkSum = this._checkSumForComponent;
            this._checkSumForComponent = null;

        }

    }

    public void characters(char[] ch, int start, int length) {

        if (inCheckSum) {
            if (this._checkSumForComponent == null) {
                this._checkSumForComponent = new String(ch, start, length);
            }
            else {
                this._checkSumForComponent = this._checkSumForComponent + new String(ch, start, length);
            }
        }

    }

    public String getChecksum() {
        return this._checkSum;
    }

    public String getLmd() {
        return this._lmd;
    }

    public String getItemHref() {
        return this._itemHref;
    }

    public String getComponentHref() {
        return this._componentHref;
    }

}
