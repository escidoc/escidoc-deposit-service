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
import de.escidoc.core.resources.common.properties.PublicStatus;
import de.escidoc.core.resources.om.item.component.ChecksumAlgorithm;
import de.escidoc.core.resources.om.item.component.Component;

public class ReingestTask {

    private static final Logger LOG = LoggerFactory.getLogger(ReingestTask.class);

    private static final String PREFIX_FAILED = "failed_";

    private String providedCheckSum;

    private File configDir;

    private File content;

    private Properties configuration;

    private SessionManager sessionManager;

    private boolean isSessionFailed;

    private ItemHandlerClient itemClient;

    public ReingestTask(SessionManager sessionManager, Properties configuration, File content, File configDir,
        String providedCheckSum) throws DepositorException {
        Preconditions.checkNotNull(sessionManager, "sessionManager is null: %s", sessionManager);
        Preconditions.checkNotNull(configuration, "configuration is null: %s", configuration);
        Preconditions.checkNotNull(content, "contentFile is null: %s", content);
        Preconditions.checkNotNull(configDir, "configurationDirectory is null: %s", configDir);

        this.sessionManager = sessionManager;
        this.configuration = configuration;
        this.content = content;
        this.configDir = configDir;

        assignCheckSum(configuration, content, providedCheckSum);
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

    public void execute() {
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

    private String getFileName() {
        return content.getName();
    }

    private boolean renameFile(String toPrepend) {
        return content.renameTo(new File(configDir, toPrepend + getFileName()));
    }

    private String getConfigurationId() {
        return configuration.getProperty(Constants.PROPERTY_CONFIGURATION_ID);
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

    public boolean deleteContentFile() {
        return content.delete();
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

    private void handleFailedIngest() {
        renameFileName(PREFIX_FAILED);
        isSessionFailed = true;
        sessionManager.addToFailedConfigurations(getConfigurationId());
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
}
