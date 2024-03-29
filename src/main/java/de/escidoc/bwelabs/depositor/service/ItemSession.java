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
package de.escidoc.bwelabs.depositor.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import org.escidoc.core.client.ingest.exceptions.ConfigurationException;
import org.escidoc.core.client.ingest.exceptions.IngestException;
import org.escidoc.core.client.ingest.filesystem.FileIngester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import de.escidoc.bwelabs.deposit.Configuration;
import de.escidoc.bwelabs.depositor.error.DepositorException;
import de.escidoc.bwelabs.depositor.utility.Utility;
import de.escidoc.core.client.ItemHandlerClient;
import de.escidoc.core.client.exceptions.EscidocException;
import de.escidoc.core.client.exceptions.InternalClientException;
import de.escidoc.core.client.exceptions.TransportException;
import de.escidoc.core.client.interfaces.ItemHandlerClientInterface;
import de.escidoc.core.resources.common.properties.PublicStatus;
import de.escidoc.core.resources.om.item.component.ChecksumAlgorithm;
import de.escidoc.core.resources.om.item.component.Component;

/**
 * A class handles a storage of content files into an infrastructure.
 * 
 * @author ROF
 * 
 */
public class ItemSession extends Thread {

    private static final String PREFIX_FAILED = "failed_";

    private static final Logger LOG = LoggerFactory.getLogger(ItemSession.class.getName());

    private SessionManager manager;

    private String providedCheckSum;

    private File content;

    private File configDir;

    private String sessionKey;

    private Properties configuration;

    private boolean isThreadWorking;

    private boolean isSessionFailed = false;

    private ItemHandlerClientInterface itemClient;

    public ItemSession(SessionManager manager, Properties configuration, File content, File configDir,
        String providedCheckSum) throws DepositorException {
        Preconditions.checkNotNull(manager, "manager is null: %s", manager);
        Preconditions.checkNotNull(configuration, "configuration is null: %s", configuration);
        Preconditions.checkNotNull(content, "contentFile is null: %s", content);
        Preconditions.checkNotNull(configDir, "configurationDirectory is null: %s", configDir);

        this.manager = manager;
        this.configuration = configuration;
        this.content = content;
        this.configDir = configDir;

        assignCheckSum(configuration, content, providedCheckSum);
        createUniqueKey();

        isThreadWorking = true;
        setName("Session-" + sessionKey + "-Retriever");
    }

    private void createUniqueKey() {
        String baseKey = "" + this.hashCode();
        if (baseKey.startsWith("-")) {
            sessionKey = "Z" + baseKey.substring(1);
        }
        else {
            sessionKey = "X" + baseKey;
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
            String itemId = ingest();
            checkChecksum(itemId);
            doRenameForSuccessful(itemId);
        }
        catch (ConfigurationException e) {
            LOG.warn("The ingest is not properperly configured. " + e.getMessage(), e);
            handleFailedIngest();
        }
        catch (IngestException e) {
            LOG.warn("Fail to ingest " + e.getMessage(), e);
            handleFailedIngest();
        }
    }

    private void handleFailedIngest() {
        renameFileName(PREFIX_FAILED);
        isSessionFailed = true;
        manager.addToFailedConfigurations(getConfigurationId());
    }

    private void renameFileName(String prefix) {
        boolean isSuccesfull = renameFile(prefix);
        if (isSuccesfull) {
            content = new File(configDir, prefix + getFileName());
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
        File renamedFile = new File(configDir, "successful_" + getFileName());
        boolean isRenameSuccesful = content.renameTo(renamedFile);
        if (isRenameSuccesful) {
            // workaround because of a bug in Java1.5
            // TODO check if the workaround still nesesassary
            content = renamedFile;
        }
        else {
            LOG.error("A content file " + getFileName() + " could not be renamed to a 'successful_" + getFileName()
                + "'." + " for a configuration with id " + getConfigurationId());
        }
    }

    /**
     * @param itemId
     * @return
     * @throws IngestException
     *             If checksum of ingested Item is not as expected.
     */
    private void checkChecksum(String itemId) throws IngestException {
        try {
            itemClient = new ItemHandlerClient(new URL(getBaseUri()));
            itemClient.setHandle(getUserHandle());
            Component comp = itemClient.retrieve(itemId).getComponents().get(0);
            ChecksumAlgorithm algorithm = comp.getProperties().getChecksumAlgorithm();
            if (algorithm.equals(ChecksumAlgorithm.MD5)) {
                String checksum = comp.getProperties().getChecksum();
                if (providedCheckSum.equalsIgnoreCase(checksum)) {
                    return;
                }
                throw new IngestException(
                    "The provided checksum is not equals with the from eSciDoc Core calculated one");
            }
        }
        catch (MalformedURLException e) {
            LOG.error("URL not well formed. " + e.getMessage(), e);
            throw new IngestException(e);
        }
        catch (EscidocException e) {
            LOG.error("Something wrong in eSciDoc Core: " + e.getMessage(), e);
            throw new IngestException(e);
        }
        catch (InternalClientException e) {
            LOG.error("Something wrong in escidoc java connector" + e.getMessage(), e);
            throw new IngestException(e);
        }
        catch (TransportException e) {
            LOG.error("HTTP Transport error: " + e.getMessage(), e);
            throw new IngestException(e);
        }
    }

    private String getConfigurationId() {
        return configuration.getProperty(Constants.PROPERTY_CONFIGURATION_ID);
    }

    private String ingest() throws ConfigurationException, IngestException {
        FileIngester ingester = buildFileIngester();
        ingester.setForceCreate(true);
        ingester.ingest();
        // FIXME FileIngester might be changed.
        if (ingester.getItemIDs().isEmpty()) {
            throw new IngestException("Can not get ingested item id.");
        }
        return ingester.getItemIDs().get(0);
    }

    private FileIngester buildFileIngester() {
        FileIngester ingester = new FileIngester(getBaseUri(), getUserHandle(), getContainerId());
        if (content.getName().startsWith(PREFIX_FAILED)) {
            removePrefix();
        }
        ingester.addFile(content.getPath());
        // FIXME: container content model is not needed here.
        ingester.setContainerContentModel(configuration.getProperty(Configuration.PROPERTY_CONTENT_MODEL_ID));
        ingester.setItemContentModel(configuration.getProperty(Configuration.PROPERTY_CONTENT_MODEL_ID));
        ingester.setContext(configuration.getProperty(Configuration.PROPERTY_CONTEXT_ID));
        ingester.setContentCategory("ORIGINAL");
        ingester.setInitialLifecycleStatus(PublicStatus.PENDING);
        ingester.setVisibility("public");
        ingester.setValidStatus("valid");
        ingester.setMimeType("text/plain");
        return ingester;
    }

    private void removePrefix() {
        String onlyFileName = content.getName().split("_")[1];
        Preconditions.checkState(!onlyFileName.startsWith(PREFIX_FAILED), "Removing prefix failed: " + onlyFileName);

        File renamedFile = new File(configDir, onlyFileName);
        boolean isRenameSuccesful = content.renameTo(renamedFile);
        if (isRenameSuccesful) {
            // workaround because of a bug in Java1.5
            // TODO check if the workaround still nesesassary
            content = renamedFile;
        }
        else {
            LOG.error("A content file " + getFileName() + " could not be renamed to a " + getFileName() + "'."
                + " for a configuration with id " + getConfigurationId());
        }
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

    public String getProvidedCheckSum() {
        return providedCheckSum;
    }

    public File getContentFile() {
        return content;
    }

    public File getConfigurationDirectory() {
        return configDir;
    }

    public boolean isSessionFailed() {
        return isSessionFailed;
    }

    public boolean deleteContentFile() {
        return content.delete();
    }
}