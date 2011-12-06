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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import javax.xml.parsers.SAXParserFactory;

import org.escidoc.core.client.ingest.exceptions.ConfigurationException;
import org.escidoc.core.client.ingest.exceptions.IngestException;
import org.escidoc.core.client.ingest.filesystem.FileIngester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import de.escidoc.bwelabs.deposit.Configuration;
import de.escidoc.bwelabs.depositor.error.DepositorException;
import de.escidoc.bwelabs.depositor.utility.Utility;
import de.escidoc.core.resources.common.properties.PublicStatus;

/**
 * A class handles a storage of content files into an infrastructure.
 * 
 * @author ROF
 * 
 */
public class ItemSession extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(ItemSession.class.getName());

    private static SAXParserFactory _saxParserFactory;

    private SessionManager manager;

    private String providedCheckSum;

    private String contentFilePath;

    private File content;

    private File configDir;

    private String sessionKey;

    private Properties configuration;

    private boolean isThreadWorking;

    private boolean isSessionFailed = false;

    public ItemSession(SessionManager manager, Properties configuration, File content, File configDir,
        String providedCheckSum) throws DepositorException {
        Preconditions.checkNotNull(manager, "manager is null: %s", manager);
        Preconditions.checkNotNull(configuration, "configuration is null: %s", configuration);
        Preconditions.checkNotNull(content, "contentFile is null: %s", content);
        Preconditions.checkNotNull(configDir, "configurationDirectory is null: %s", configDir);

        this.manager = manager;
        this.configuration = configuration;
        // contentFile is created from configurationDirectory and a filename, so
        // configurationDirectory is not needed to reconstruct the path
        // configurationDirectory.getName() + "/" + contentFile.getName();
        this.content = content;
        this.contentFilePath = content.getPath();
        this.configDir = configDir;

        assignCheckSum(configuration, content, providedCheckSum);
        // _configurationId = configId;
        createUniqueKey();
        isThreadWorking = true;

        setName("Session-" + sessionKey + "-Retriever");
    }

    private void createUniqueKey() {
        String s = "" + this.hashCode();
        if (s.startsWith("-")) {
            sessionKey = "Z" + s.substring(1);
        }
        else {
            sessionKey = "X" + s;
        }
    }

    private void assignCheckSum(Properties configuration, File content, String providedCheckSum)
        throws DepositorException {
        if (providedCheckSum == null) {
            // if configuration is restoring from a file system, check sum for
            // content files must be calculated again
            FileInputStream is = null;
            try {
                is = new FileInputStream(content);
            }
            catch (FileNotFoundException e) {
                String message =
                    "Error on Restoring configurations from last run: "
                        + "unexpected exception while reading a content file " + content.getName()
                        + " of the configuration with id "
                        + configuration.getProperty(Constants.PROPERTY_CONFIGURATION_ID);
                // _LOG.error(message);
                throw new DepositorException(message);
            }
            String checkSumAlg = configuration.getProperty(Constants.PROPERTY_CHECKSUM_ALGORITHM);
            MessageDigest md = null;
            try {
                md = MessageDigest.getInstance(checkSumAlg);
            }
            catch (NoSuchAlgorithmException e) {
                String message =
                    "Error on Restoring configurations from last run: unexpected exception " + e.getMessage();
                // _LOG.error(message);
                throw new DepositorException(message);
            }
            byte buffer[] = new byte[5000];
            int numread;

            try {
                while ((numread = is.read(buffer, 0, 5000)) > 0) {
                    md.update(buffer, 0, numread);
                }
                is.close();
            }
            catch (IOException e) {
                String message =
                    "Error on restoring configurations from last run: "
                        + "unexpected exception while calculating a check sum for a content file " + content.getName()
                        + " of the configuration with id "
                        + configuration.getProperty(Constants.PROPERTY_CONFIGURATION_ID) + e.getMessage();
                // _LOG.error(message);
                throw new DepositorException(message);
            }

            byte[] digest = md.digest();
            this.providedCheckSum = Utility.byteArraytoHexString(digest);
        }
        else {
            this.providedCheckSum = providedCheckSum;
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    /**
     * Content file storage thread.
     */
    public void run() {
        LOG.info(sessionKey + " retrieval thread started");
        String configurationId = getConfigurationId();
        manager.addSession(this, configurationId);
        manager.increaseThreadsNumber();
        storeFileInToInfrastructure();
        manager.decreaseThreadsNumber();
        isThreadWorking = false;

    }

    // /////////////////////////////////////////////////////////////////////////
    /**
     * Method calls a method EscidocUtility.createContentItemXml() by providing it with a local path to a file relative
     * to a base directory of Depositor service in order to create an item-xml. It makes two attempts to store the item
     * as a member of a container with id, provided in a configuration. Then it checks if a check sum of the item binary
     * content, calculated by the infrastructure matches a check sum, contained by instance variable. If a check sum
     * does not matches, it calls a method reloadingContentItemInToInfrastructure() to update the item. If the item was
     * not successful stored into the infrastructure, the method set a boolean instance variable '_sessionFailed' to a
     * value 'true'. Otherwise this instance variable retains its initial value 'false'. Other threads can request the
     * result of the storage into an infrastructure by access the value of this instance variable.
     * 
     */
    private void storeFileInToInfrastructure() {
        try {
            ingest();
            String itemId = "Unknown item id";
            // TODO compare ingested content checksum with the provided checksum
            // compare checksum from infrastructure with own one
            if (isChecksumEquals()) {
                doRenameForSuccessful(itemId);
            }
            else {
                doIngestOneMoreTime();
                // TODO what happens if it still unequals?
            }
        }
        catch (ConfigurationException e) {
            LOG.error("The ingest is not properperly configured. " + e.getMessage(), e);
            handleFailedIngest();
        }
        catch (IngestException e) {
            LOG.error("Fail to ingest " + e.getMessage(), e);
            handleFailedIngest();
        }

    }

    private void handleFailedIngest() {
        renameFileName();
        isSessionFailed = true;
        manager.addToFailedConfigurations(getConfigurationId());
    }

    private void renameFileName() {
        // TODO if ingest fails, rename content file to "failed_"...
        boolean isSuccesfull = renameFile("failed_");
        if (isSuccesfull) {
            // workaround because of a bug in Java1.5
            content = new File(configDir, "failed_" + getFileName());
        }
        else {
            LOG.error("A content file " + getFileName() + " could not be renamed to a 'failed_" + getFileName() + "'."
                + " for a configuration with id " + getConfigurationId());
        }
    }

    private String getFileName() {
        return content.getName();
    }

    private boolean renameFile(String toPrepend) {
        return content.renameTo(new File(configDir, toPrepend + getFileName()));
    }

    private void doRenameForSuccessful(String itemId) {
        LOG.info("Successfully created an item with id " + itemId + " containing a file with a name " + getFileName()
            + " for a configuration with id " + getConfigurationId() + " belonging to the experiment with id "
            + getContainerId() + ".");

        // TODO if unsuccessful item and references must be removed
        // _contentFile.delete();
        boolean isRenameSuccesful = content.renameTo(new File(configDir, "successful_" + getFileName()));
        if (isRenameSuccesful) {
            // workaround because of a bug in Java1.5
            // TODO check if the workaround still necesassary
            content = new File(configDir, "successful_" + getFileName());
        }
        else {
            LOG.error("A content file " + getFileName() + " could not be renamed to a 'successful_" + getFileName()
                + "'." + " for a configuration with id " + getConfigurationId());
        }
    }

    private void doIngestOneMoreTime() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private boolean isChecksumEquals() {
        // String createdCheckSum = ih.getChecksum();
        // String itemHref = ih.getItemHref();
        // itemId = Utility.getId(itemHref);
        // String componentHref = ih.getComponentHref();
        // String lmd = ih.getLmd();
        // throw new UnsupportedOperationException("Not yet implemented");
        return true;
    }

    private String getConfigurationId() {
        String configurationId = configuration.getProperty(Constants.PROPERTY_CONFIGURATION_ID);
        return configurationId;
    }

    private void ingest() throws ConfigurationException, IngestException {
        FileIngester ingester = buildFileIngester();
        ingester.setForceCreate(true);
        ingester.ingest();
        // FIXME metadata extraction
    }

    private FileIngester buildFileIngester() {
        FileIngester ingester = new FileIngester(getBaseUri(), getUserHandle(), getContainerId());
        ingester.addFile(contentFilePath);
        // FIXME: container content model is not needed here.
        ingester.setContainerContentModel(configuration.getProperty(Configuration.PROPERTY_CONTENT_MODEL_ID));
        ingester.setItemContentModel(configuration.getProperty(Configuration.PROPERTY_CONTENT_MODEL_ID));
        ingester.setContext(configuration.getProperty(Configuration.PROPERTY_CONTEXT_ID));
        ingester.setContentCategory("ORIGINAL");
        ingester.setInitialLifecycleStatus(PublicStatus.PENDING);
        ingester.setVisibility("public");
        ingester.setValidStatus("valid");
        ingester.setMimeType("text/xml");
        return ingester;
    }

    private String getUserHandle() {
        return configuration.getProperty(Constants.PROPERTY_USER_HANDLE);
    }

    private String getContainerId() {
        return configuration.getProperty(Constants.PROPERTY_EXPERIMENT_ID);
    }

    private String getBaseUri() {
        return configuration.getProperty(Constants.PROPERTY_INFRASTRUCTURE_ENDPOINT);
    }

    /**
     * Method makes two attempts to update an item with a provided id, it checks if a check sum of the item binary
     * content, calculated by the infrastructure matches a check sum, contained by instance variable. If a check sum
     * does not matches or any exception occurs, it deletes the item from a container with a provided id.
     * 
     * @param escidocBaseUrl
     * @param itemId
     * @param containerId
     * @param handle
     * @param itemXml
     * @return true - if a check sum of the updated item matches the instance check sum, false -otherwise.
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

    public boolean isFinished() {
        return !isThreadWorking;
    }

    public String get_providedCheckSum() {
        return providedCheckSum;
    }

    public File get_contentFile() {
        return content;
    }

    public File get_configurationDirectory() {
        return configDir;
    }

    public boolean isSessionFailed() {
        return isSessionFailed;
    }

    public boolean deleteContentFile() {
        return content.delete();
    }
}