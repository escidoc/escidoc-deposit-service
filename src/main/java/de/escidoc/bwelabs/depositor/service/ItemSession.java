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
package de.escidoc.bwelabs.depositor.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.escidoc.core.client.ingest.exceptions.ConfigurationException;
import org.escidoc.core.client.ingest.exceptions.IngestException;
import org.escidoc.core.client.ingest.filesystem.FileIngester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import de.escidoc.bwelabs.deposit.Configuration;
import de.escidoc.bwelabs.depositor.error.DepositorException;
import de.escidoc.bwelabs.depositor.saxHandler.ContainerHandler;
import de.escidoc.bwelabs.depositor.saxHandler.ItemHandler;
import de.escidoc.bwelabs.depositor.utility.Utility;
import de.escidoc.core.resources.common.properties.PublicStatus;

/**
 * A class handles a storage of content files into an infrastructure.
 * 
 * @author ROF
 * 
 */
public class ItemSession extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(ItemSession.class
	    .getName());

    private static SAXParserFactory _saxParserFactory;

    private SessionManager _manager;

    private String _providedCheckSum;

    private String _pathToContentFile;

    private File _contentFile;

    private File _configurationDirectory;

    private String _sessionKey;

    private Properties _configuration;

    private boolean _threadWorking;

    private boolean _sessionFailed = false;

    public ItemSession(SessionManager manager, Properties configuration,
	    File contentFile, File configurationDirectory,
	    String providedCheckSum) throws DepositorException {
	_configuration = configuration;
	_manager = manager;
	// contentFile is created from configurationDirectory and a filename, so
	// configurationDirectory is not needed to reconstruct the path
	_pathToContentFile = contentFile.getPath();
	// configurationDirectory.getName() + "/" + contentFile.getName();
	_contentFile = contentFile;
	_configurationDirectory = configurationDirectory;
	if (providedCheckSum == null) {
	    // if configuration is restoring from a file system, check sum for
	    // content files must be calculated again
	    FileInputStream is = null;
	    try {
		is = new FileInputStream(contentFile);
	    } catch (FileNotFoundException e) {
		String message = "Error on Restoring configurations from last run: "
			+ "unexpected exception while reading a content file "
			+ contentFile.getName()
			+ " of the configuration with id "
			+ configuration
				.getProperty(Constants.PROPERTY_CONFIGURATION_ID);
		// _LOG.error(message);
		throw new DepositorException(message);
	    }
	    String checkSumAlg = configuration
		    .getProperty(Constants.PROPERTY_CHECKSUM_ALGORITHM);
	    MessageDigest md = null;
	    try {
		md = MessageDigest.getInstance(checkSumAlg);
	    } catch (NoSuchAlgorithmException e) {
		String message = "Error on Restoring configurations from last run: unexpected exception "
			+ e.getMessage();
		// _LOG.error(message);
		throw new DepositorException(message);
	    }
	    if (is != null) {
		byte buffer[] = new byte[5000];
		int numread;

		try {
		    while ((numread = is.read(buffer, 0, 5000)) > 0) {
			md.update(buffer, 0, numread);
		    }
		    is.close();
		} catch (IOException e) {
		    String message = "Error on restoring configurations from last run: "
			    + "unexpected exception while calculating a check sum for a content file "
			    + contentFile.getName()
			    + " of the configuration with id "
			    + configuration
				    .getProperty(Constants.PROPERTY_CONFIGURATION_ID)
			    + e.getMessage();
		    // _LOG.error(message);
		    throw new DepositorException(message);
		}

		byte[] digest = md.digest();
		_providedCheckSum = Utility.byteArraytoHexString(digest);
	    }
	} else {
	    _providedCheckSum = providedCheckSum;
	}
	// _configurationId = configId;
	// make a unique key for this session
	String s = "" + this.hashCode();
	if (s.startsWith("-")) {
	    _sessionKey = "Z" + s.substring(1);
	} else {
	    _sessionKey = "X" + s;
	}

	_threadWorking = true;

	setName("Session-" + _sessionKey + "-Retriever");

    }

    // /////////////////////////////////////////////////////////////////////////
    /**
     * Content file storage thread.
     */
    public void run() {
	LOG.info(_sessionKey + " retrieval thread started");

	// after this
	// point, we
	// depend on
	// the session
	// manager to
	String configurationId = _configuration
		.getProperty(Constants.PROPERTY_CONFIGURATION_ID);
	_manager.addSession(this, configurationId);
	_manager.increaseThreadsNumber();
	storeFileInToInfrastructure();
	_manager.decreaseThreadsNumber();
	_threadWorking = false;

    }

    // /////////////////////////////////////////////////////////////////////////
    /**
     * Method calls a method EscidocUtility.createContentItemXml() by providing
     * it with a local path to a file relative to a base directory of Depositor
     * service in order to create an item-xml. It makes two attempts to store
     * the item as a member of a container with id, provided in a configuration.
     * Then it checks if a check sum of the item binary content, calculated by
     * the infrastructure matches a check sum, contained by instance variable.
     * If a check sum does not matches, it calls a method
     * reloadingContentItemInToInfrastructure() to update the item. If the item
     * was not successful stored into the infrastructure, the method set a
     * boolean instance variable '_sessionFailed' to a value 'true'. Otherwise
     * this instance variable retains its initial value 'false'. Other threads
     * can request the result of the storage into an infrastructure by access
     * the value of this instance variable.
     * 
     */
    private void storeFileInToInfrastructure() {
	boolean createdItemSuccessful = false;
	String configurationId = _configuration
		.getProperty(Constants.PROPERTY_CONFIGURATION_ID);

	// FIXME metadata is created in deposit api not here
	// String itemMetadata = EscidocUtility
	// .getDescriptiveMetadata(_contentFile.getName());
	// String itemXml = EscidocUtility.createContentItemXml(_configuration,
	// _pathToContentFile, itemMetadata, null, null, null);
	String containerId = _configuration
		.getProperty(Constants.PROPERTY_EXPERIMENT_ID);

	String escidocBaseUrl = _configuration
		.getProperty(Constants.PROPERTY_INFRASTRUCTURE_ENDPOINT);

	String handle = _configuration
		.getProperty(Constants.PROPERTY_USER_HANDLE);

	// PostMethod post = null;
	// String itemId = null;
	// try {
	// post = EscidocConnector.createItemInContainer(escidocBaseUrl,
	// containerId, handle, itemXml);
	// }

	FileIngester ingester = new FileIngester(escidocBaseUrl, handle,
		containerId);

	ingester.addFile(_pathToContentFile);
	ingester.setItemContentModel(_configuration
		.getProperty(Configuration.PROPERTY_CONTENT_MODEL_ID));
	ingester.setContext(_configuration
		.getProperty(Configuration.PROPERTY_CONTEXT_ID));
	ingester.setContentCategory("ORIGINAL");
	ingester.setInitialLifecycleStatus(PublicStatus.PENDING); // ingester.getLifecycleStatus().get(0));
	ingester.setMimeType("text/xml"); // ingester.getMimeTypes().get(0));

	try {
	    ingester.ingest();
	    // FIXME
	} catch (ConfigurationException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	} catch (IngestException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}

	String itemId = null;

	// String createdCheckSum = ih.getChecksum();
	// String itemHref = ih.getItemHref();
	// itemId = Utility.getId(itemHref);
	// String componentHref = ih.getComponentHref();
	// String lmd = ih.getLmd();

	// compare checksum from infrastructure with own one
	// if (createdCheckSum.equals(_providedCheckSum)) {
	// createdItemSuccessful = true;
	// } else {
	// // upload again and check
	// }

	// TODO if unsuccessful item and references must be removed

	LOG.info("Successfully created an item with id " + itemId
		+ " containing a file with a name " + _contentFile.getName()
		+ " for a configuration with id " + configurationId
		+ " belonging to the experiment with id " + containerId + ".");
	// _contentFile.delete();
	String fileName = _contentFile.getName();
	boolean success = _contentFile.renameTo(new File(
		_configurationDirectory, "successful_" + fileName));
	if (!success) {
	    LOG.error("A content file " + fileName
		    + " could not be renamed to a 'successful_" + fileName
		    + "'." + " for a configuration with id " + configurationId);

	} else {
	    // workaround because of a bug in Java1.5
	    _contentFile = new File(_configurationDirectory, "successful_"
		    + fileName);
	}

	// TODO if failed rename content file to "failed_"...
	// boolean success = _contentFile.renameTo(new File(
	// _configurationDirectory, "failed_" + fileName));
	// if (!success) {
	// LOG.error("A content file " + fileName
	// + " could not be renamed to a 'failed_" + fileName
	// + "'." + " for a configuration with id "
	// + configurationId);
	//
	// } else {
	// // workaround because of a bug in Java1.5
	// _contentFile = new File(_configurationDirectory, "failed_"
	// + fileName);
	// }

	_sessionFailed = true;
	_manager.addToFailedConfigurations(configurationId);

    }

    /**
     * Method makes two attempts to update an item with a provided id, it checks
     * if a check sum of the item binary content, calculated by the
     * infrastructure matches a check sum, contained by instance variable. If a
     * check sum does not matches or any exception occurs, it deletes the item
     * from a container with a provided id.
     * 
     * @param escidocBaseUrl
     * @param itemId
     * @param containerId
     * @param handle
     * @param itemXml
     * @return true - if a check sum of the updated item matches the instance
     *         check sum, false -otherwise.
     */
    // private boolean reloadingContentItemInToInfrastructure(
    // final String escidocBaseUrl, final String itemId,
    // final String containerId, final String handle, final String itemXml) {
    // boolean succesfullyReloaded = false;
    // PutMethod put = null;
    // try {
    // put = EscidocConnector.updateItem(escidocBaseUrl, itemId, handle,
    // itemXml);
    // } catch (Throwable e) {
    //
    // LOG.error("Error while updating the item with id: " + itemId + " "
    // + e.getMessage());
    // LOG.error("Doing a next attempt.");
    // try {
    // Thread.sleep(250);
    // } catch (Exception e1) {
    //
    // }
    // try {
    // put = EscidocConnector.updateItem(escidocBaseUrl, itemId,
    // handle, itemXml);
    // } catch (Throwable e2) {
    // LOG.error("Error while second attempt to update the item referencing a content file to the container with id "
    // + containerId + " : " + e2.getMessage());
    // }
    //
    // }
    // if (put != null) {
    // InputStream is = null;
    //
    // try {
    // is = put.getResponseBodyAsStream();
    // } catch (IOException e) {
    // LOG.error("Error while getting the input stream with a created item: "
    // + e.getMessage());
    // }
    // if (is != null) {
    // ItemHandler ih = null;
    // try {
    // ih = parseCreatedItem(is);
    // } catch (DepositorException e2) {
    // LOG.error("Error while parsing the input stream with a created item: "
    // + e2.getMessage());
    // }
    // try {
    // is.close();
    // put.releaseConnection();
    // } catch (IOException e) {
    // LOG.error("Error while closing the input stream with a created item: "
    // + e.getMessage());
    // }
    // if (ih != null) {
    // String createdCheckSum = ih.getChecksum();
    //
    // if (createdCheckSum.equals(_providedCheckSum)) {
    // succesfullyReloaded = true;
    // } else {
    // String message =
    // "Error: a checksum of the file changed while updating the binary content of the item "
    // + itemId
    // + ". This item will be removed from a container with id "
    // + containerId + ".";
    // LOG.error(message);
    // // Delete the created item from a container, but first
    // // retrieve properties of the container to
    // // fetch its last modification date
    // getContainerLmdAndDeleteItem(escidocBaseUrl,
    // containerId, handle, itemId);
    // }
    // } else {
    // // Delete the created item from a container, but first
    // // retrieve properties of the container to
    // // fetch its last modification date
    // getContainerLmdAndDeleteItem(escidocBaseUrl, containerId,
    // handle, itemId);
    // }
    //
    // } else {
    // // Delete the created item from a container, but first
    // // retrieve properties of the container to
    // // fetch its last modification date
    // getContainerLmdAndDeleteItem(escidocBaseUrl, containerId,
    // handle, itemId);
    // }
    // } else {
    // // Delete the created item from a container, but first
    // // retrieve properties of the container to
    // // fetch its last modification date
    // getContainerLmdAndDeleteItem(escidocBaseUrl, containerId, handle,
    // itemId);
    // }
    // return succesfullyReloaded;
    //
    // }

    // private void getContainerLmdAndDeleteItem(final String escidocBaseUrl,
    // final String containerId, final String handle, final String itemId) {
    // GetMethod get = retrieveContainerProperties(escidocBaseUrl,
    // containerId, handle);
    // String lmd = null;
    // if (get != null) {
    // try {
    // InputStream getIs = get.getResponseBodyAsStream();
    // try {
    // lmd = parseContainer(getIs);
    // } catch (DepositorException e1) {
    // LOG.error("Error while parsing container last-modification-date from the input stream with properties of container with id "
    // + containerId
    // +
    // " . Can not delete an item from the container without last-modification-date of the container. "
    // + e1.getMessage());
    // }
    // try {
    // getIs.close();
    // get.releaseConnection();
    // } catch (IOException e) {
    // LOG.error("Error while closing the input stream with properties of container: "
    // + e.getMessage());
    // }
    //
    // } catch (IOException e) {
    // LOG.error("Error while getting the input stream with properties of container with id "
    // + containerId
    // +
    // " . Can not delete an item from the container without last-modification-date of the container. "
    // + e.getMessage());
    // }
    //
    // if (lmd != null) {
    // deleteItemFromContainer(escidocBaseUrl, itemId, containerId,
    // handle, lmd);
    // } else {
    // LOG.error("Error while parsing container last-modification-date from the input stream "
    // +
    // "with container properties. Can not delete an item from the container without "
    // + "last-modification-date of the container.");
    // }
    // } else {
    // LOG.error(" Can not delete an item from the container without last-modification-date of the container. ");
    // }
    // }

    // private void deleteItemFromContainer(final String escidocBaseUrl,
    // final String itemId, final String containerId, final String handle,
    // final String lmd) {
    // try {
    // EscidocConnector.deleteItemFromContainer(escidocBaseUrl, itemId,
    // containerId, handle, lmd);
    // } catch (Throwable e1) {
    // LOG.error("Error while deleting item with id " + itemId
    // + " from the container with id " + containerId + " "
    // + e1.getMessage());
    // LOG.error("Doing a next attempt.");
    // try {
    // Thread.sleep(250);
    // } catch (Exception e) {
    // }
    // try {
    // EscidocConnector.deleteItemFromContainer(escidocBaseUrl,
    // itemId, containerId, handle, lmd);
    // } catch (Throwable e) {
    // LOG.error("Error while second attempt to delete item with id "
    // + itemId + " from the container with id " + containerId
    // + " " + e.getMessage());
    // }
    // }
    // }

    // private GetMethod retrieveContainerProperties(final String
    // escidocBaseUrl,
    // final String containerId, final String handle) {
    // GetMethod get = null;
    // try {
    // get = EscidocConnector.pingContainer(escidocBaseUrl, containerId,
    // handle);
    // } catch (Throwable e1) {
    // LOG.error("Error while retrieving properties of a container container with id: "
    // + containerId + " " + e1.getMessage());
    // LOG.error("Doing a next attempt.");
    // try {
    // Thread.sleep(250);
    // } catch (Exception e) {
    // }
    // try {
    // get = EscidocConnector.pingContainer(escidocBaseUrl,
    // containerId, handle);
    // } catch (Throwable e) {
    // LOG.error("Error while second attempt to retrieve properties of the container with id: "
    // + containerId + " " + e.getMessage());
    // }
    // }
    // return get;
    // }

    /**
     * Method parses an item xml, contained in a provided input stream.
     * 
     * @param is
     *            input stream
     * @return an instance of a SAX-handler contained data, parsed from a item
     *         xml
     * @throws DepositorException
     */
    private ItemHandler parseCreatedItem(InputStream is)
	    throws DepositorException {
	if (_saxParserFactory == null) {

	    _saxParserFactory = SAXParserFactory.newInstance();
	    _saxParserFactory.setValidating(false);
	    _saxParserFactory.setNamespaceAware(true);
	}
	SAXParser parser = null;
	try {
	    parser = _saxParserFactory.newSAXParser();
	} catch (ParserConfigurationException e) {
	    // FIXME give a message
	    LOG.error(e.getMessage(), e);
	    throw new DepositorException(e.getMessage(), e);
	} catch (SAXException e) {
	    // FIXME give a message
	    LOG.error(e.getMessage(), e);
	    throw new DepositorException(e.getMessage(), e);
	}

	ItemHandler ih = new ItemHandler();
	try {
	    parser.parse(is, ih);

	} catch (SAXException e) {
	    // FIXME give a message
	    LOG.error(e.getMessage(), e);
	    throw new DepositorException(e.getMessage(), e);
	} catch (IOException e) {
	    // FIXME give a message
	    LOG.error(e.getMessage(), e);
	    throw new DepositorException(e.getMessage(), e);
	}

	return ih;
    }

    /**
     * Method parses a container xml, contained in a provided input stream.
     * 
     * @param is
     *            input stream
     * @return last-modification-date, parsed from a container-xml
     * @throws DepositorException
     */
    private String parseContainer(InputStream is) throws DepositorException {
	if (_saxParserFactory == null) {

	    _saxParserFactory = SAXParserFactory.newInstance();
	    _saxParserFactory.setValidating(false);
	    _saxParserFactory.setNamespaceAware(true);
	}
	SAXParser parser = null;
	try {
	    parser = _saxParserFactory.newSAXParser();
	} catch (ParserConfigurationException e) {
	    // FIXME give a message
	    LOG.error(e.getMessage(), e);
	    throw new DepositorException(e.getMessage(), e);
	} catch (SAXException e) {
	    // FIXME give a message
	    LOG.error(e.getMessage(), e);
	    throw new DepositorException(e.getMessage(), e);
	}

	ContainerHandler ch = new ContainerHandler();
	try {
	    parser.parse(is, ch);

	} catch (SAXException e) {
	    // FIXME give a message
	    LOG.error(e.getMessage(), e);
	    throw new DepositorException(e.getMessage(), e);
	} catch (IOException e) {
	    // FIXME give a message
	    LOG.error(e.getMessage(), e);
	    throw new DepositorException(e.getMessage(), e);
	}

	return ch.getLmd();
    }

    public boolean isFinished() {
	return !_threadWorking;
    }

    public String get_providedCheckSum() {
	return _providedCheckSum;
    }

    public File get_contentFile() {
	return _contentFile;
    }

    public File get_configurationDirectory() {
	return _configurationDirectory;
    }

    public boolean isSessionFailed() {
	return _sessionFailed;
    }

    public void deleteContentFile() {
	_contentFile.delete();
    }

}