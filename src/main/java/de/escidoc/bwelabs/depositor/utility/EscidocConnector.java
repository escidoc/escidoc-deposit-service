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

import de.escidoc.bwelabs.depositor.error.ApplicationException;
import de.escidoc.bwelabs.depositor.error.ConnectionException;
import de.escidoc.bwelabs.depositor.error.DepositorException;
import de.escidoc.bwelabs.depositor.error.InfrastructureException;

/**
 * An utility class for requests to the Escidoc infrastructure.
 * 
 * @author ROF
 * 
 */
public class EscidocConnector {
    private static ConnectionUtility utility = null;

    public static void init() {

	utility = new ConnectionUtility();
    }

    /**
     * Method calls a method utility.getEscidoc() and provide it with an URL
     * matching a REST-URL of the ContainerHandler.retrieveProperties() method
     * and a valid user handle.
     * 
     * @param eScidocBaseUrl
     * @param containerId
     * @param handle
     * @return
     * @throws ApplicationException
     * @throws InfrastructureException
     * @throws ConnectionException
     */
    public static GetMethod pingContainer(final String eScidocBaseUrl,
	    final String containerId, String handle)
	    throws ApplicationException, InfrastructureException,
	    ConnectionException {

	String url = null;
	if (eScidocBaseUrl.endsWith("/")) {
	    url = eScidocBaseUrl + "ir/container/" + containerId
		    + "/properties";
	} else {
	    url = eScidocBaseUrl + "/ir/container/" + containerId
		    + "/properties";
	}
	GetMethod get = null;

	get = utility.getEscidoc(url, handle);

	get.releaseConnection();
	return get;
    }

    /**
     * Method calls a method utility.postEscidoc() and provide it with an URL
     * matching a REST-URL of the ContainerHandler.createItem() method, item-xml
     * and a valid user handle.
     * 
     * @param eScidocBaseUrl
     * @param containerId
     * @param handle
     * @param itemXml
     * @return
     * @throws DepositorException
     * @throws InfrastructureException
     * @throws ApplicationException
     * @throws ConnectionException
     */
    public static PostMethod createItemInContainer(final String eScidocBaseUrl,
	    final String containerId, String handle, String itemXml)
	    throws DepositorException, InfrastructureException,
	    ApplicationException, ConnectionException {

	String url = null;
	if (eScidocBaseUrl.endsWith("/")) {
	    url = eScidocBaseUrl + "ir/container/" + containerId
		    + "/create-item";
	} else {
	    url = eScidocBaseUrl + "/ir/container/" + containerId
		    + "/create-item";
	}
	String body = itemXml;

	return utility.postEscidoc(url, body, handle);

    }

    /**
     * 
     * Method calls a method utility.putEscidoc() and provide it with an URL
     * matching a REST-URL of the ItemHandler.update() method, item-xml and a
     * valid user handle.
     * 
     * @param eScidocBaseUrl
     * @param itemId
     * @param handle
     * @param itemXml
     * @return
     * @throws DepositorException
     * @throws InfrastructureException
     * @throws ApplicationException
     * @throws ConnectionException
     */
    // public static PutMethod updateItem(
    // final String eScidocBaseUrl, final String itemId, String handle,
    // String itemXml) throws DepositorException, InfrastructureException,
    // ApplicationException, ConnectionException {
    //
    // String url = null;
    // if (eScidocBaseUrl.endsWith("/")) {
    // url = eScidocBaseUrl + "ir/item/" + itemId;
    // }
    // else {
    // url = eScidocBaseUrl + "/ir/item/" + itemId;
    // }
    // String body = itemXml;
    //
    // return utility.putEscidoc(url, body, handle);
    //
    // }

    /**
     * 
     * Method calls a method utility.deleteEscidoc() with an URL matching a
     * REST-URL of the ItemHandler.delete() method and a valid user handle.
     * 
     * 
     * @param eScidocBaseUrl
     * @param itemId
     * @param handle
     * @throws DepositorException
     * @throws InfrastructureException
     * @throws ApplicationException
     * @throws ConnectionException
     */
    public static void deleteItem(final String eScidocBaseUrl,
	    final String itemId, String handle) throws DepositorException,
	    InfrastructureException, ApplicationException, ConnectionException {

	String url = null;
	if (eScidocBaseUrl.endsWith("/")) {
	    url = eScidocBaseUrl + "ir/item/" + itemId;
	} else {
	    url = eScidocBaseUrl + "/ir/item/" + itemId;
	}

	utility.deleteEscidoc(url, handle);

    }

    /**
     * Method calls a method utility.postEscidoc() and provide it with an URL
     * matching a REST-URL of the ContainerHandler.removeMembers() method, xml
     * with task parameters and a valid user handle.
     * 
     * @param eScidocBaseUrl
     * @param itemId
     *            - item to remove
     * @param containerId
     *            - container id
     * @param handle
     *            -valid user handle
     * @param lastModDate
     *            - last modification date of the container with provided id
     * @throws DepositorException
     * @throws InfrastructureException
     * @throws ApplicationException
     * @throws ConnectionException
     */
    public static void deleteItemFromContainer(final String eScidocBaseUrl,
	    final String itemId, final String containerId, final String handle,
	    final String lastModDate) throws DepositorException,
	    InfrastructureException, ApplicationException, ConnectionException {

	String url = null;
	if (eScidocBaseUrl.endsWith("/")) {
	    url = eScidocBaseUrl + "ir/container/" + containerId
		    + "/members/remove";
	} else {
	    url = eScidocBaseUrl + "/ir/container/" + containerId
		    + "/members/remove";
	}
	String body = "<param last-modification-date=\"" + lastModDate
		+ "\"><id>" + itemId + "</id></param>";

	utility.postEscidoc(url, body, handle);
    }

}
