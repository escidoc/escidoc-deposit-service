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

import org.escidoc.core.client.ingest.exceptions.ConfigurationException;
import org.escidoc.core.client.ingest.exceptions.IngestException;
import org.escidoc.core.client.ingest.filesystem.FileIngester;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import de.escidoc.bwelabs.deposit.Configuration;
import de.escidoc.bwelabs.depositor.error.AlreadyExistException;
import de.escidoc.bwelabs.depositor.error.AlreadyExpiredException;
import de.escidoc.bwelabs.depositor.error.ApplicationException;
import de.escidoc.bwelabs.depositor.error.ConnectionException;
import de.escidoc.bwelabs.depositor.error.DepositorException;
import de.escidoc.bwelabs.depositor.error.InfrastructureException;
import de.escidoc.bwelabs.depositor.error.WrongFormatException;
import de.escidoc.bwelabs.depositor.utility.EscidocUtility;
import de.escidoc.bwelabs.depositor.utility.Utility;
import de.escidoc.core.resources.common.properties.PublicStatus;

/**
 * Provides methods which execute requests to a deposit servlet, administers threads.
 * 
 * @author ROF
 * 
 */
public class SessionManager extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class.getName());

    public static final String PROP_BASEDIR = "depositor.sessionBaseDir";

    public static final String PROP_MAX_THREAD_NUMBER = "depositor.maxThreadNumber";

    public static final String PROP_PING_INTERVAL = "depositor.pingIntervalSeconds";

    public static final String ERR_MAX_THREADS_ =
        "The depositor service is unavalible. The maximal number of threads is reached. Please try later.";

    private File m_baseDir;

    private int m_maxThreadNumber;

    private int m_threadNumber;

    private int m_pingInterval;

    private HashMap<String, Properties> m_configurations;

    private HashMap<String, String> m_configurationDirectoriesPathes = null;

    private HashMap<String, Properties> m_failedConfigurations = null;

    private HashMap<String, Properties> m_expiredSuccessfulConfigurations = null;

    private Map<String, Vector<ItemSession>> m_sessions;

    private Map<String, String> m_failedExpired_configurationDirectories;

    private Vector<String> m_expiredConfigurationsSinceLastRun;

    private Map<String, File> m_dirsFromLastRunToProcess;

    private Vector<String> m_isCleaning;

    private boolean m_threadNeedsToFinish;

    private boolean m_threadFinished;

    private String m_contentFileServletUrl;

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    private static final String PATH_FORMAT = "yyyy_MM_dd_HH_mm_ss_SSS";

    public SessionManager(Properties props, String contentFileServletUrl) throws DepositorException {
        m_threadNumber = 0;
        String dir = props.getProperty(PROP_BASEDIR);
        if (dir == null) {
            String message = "Required property missing: " + PROP_BASEDIR;
            logger.error(message);
            throw new DepositorException(message);
        }

        String threads = props.getProperty(PROP_MAX_THREAD_NUMBER);
        if (threads == null) {
            String message = "Required property missing: " + PROP_MAX_THREAD_NUMBER;
            logger.error(message);
            throw new DepositorException(message);
        }
        int threadNumber;
        try {
            threadNumber = Integer.parseInt(threads);
        }
        catch (Exception e) {
            String message = "Required property must an integer: " + PROP_MAX_THREAD_NUMBER;
            logger.error(message);
            throw new DepositorException(message);
        }

        String propertyPingInterval = props.getProperty(PROP_PING_INTERVAL);
        if (propertyPingInterval == null) {
            String message = "Required property missing: " + PROP_PING_INTERVAL;
            logger.error(message);
            throw new DepositorException(message);
        }

        try {
            int pingInterval = Integer.parseInt(propertyPingInterval);
            m_pingInterval = pingInterval;
        }
        catch (Exception e) {
            String message = "Required property must an integer: " + PROP_PING_INTERVAL;
            logger.error(message);
            throw new DepositorException(message);
        }

        m_contentFileServletUrl = contentFileServletUrl;
        init(new File(dir), threadNumber);
    }

    private void init(File baseDir, int maxThreadNumber) throws DepositorException {
        m_dirsFromLastRunToProcess = new HashMap<String, File>();
        m_sessions = new HashMap<String, Vector<ItemSession>>();
        m_failedExpired_configurationDirectories = new HashMap<String, String>();
        m_configurations = new HashMap<String, Properties>();
        m_failedConfigurations = new HashMap<String, Properties>();
        m_expiredSuccessfulConfigurations = new HashMap<String, Properties>();
        m_configurationDirectoriesPathes = new HashMap<String, String>();
        m_isCleaning = new Vector<String>();
        m_baseDir = baseDir;
        m_expiredConfigurationsSinceLastRun = new Vector<String>();
        if (!m_baseDir.exists()) {
            m_baseDir.mkdirs();
        }
        File[] dirs = m_baseDir.listFiles();
        if (dirs == null)
            throw new DepositorException("Unable to restore configuration directories within a base directory: "
                + m_baseDir.getPath());
        if (dirs.length > 0) {
            logger.info("Restoring configurations from last run...");

            for (int i = 0; i < dirs.length; i++) {
                if (dirs[i].isDirectory()) {
                    String dirName = dirs[i].getName();
                    File configurationFile = new File(dirs[i], Constants.CONFIGURATION_FILE_NAME);
                    if (!configurationFile.exists()) {
                        String message =
                            "Can not restore the configuration from the directory "
                                + m_baseDir
                                + "/"
                                + dirName
                                + " on start up of the Depositor: a configuration file does not exist in the directory.";
                        logger.error(message);
                        continue;
                    }

                    FileInputStream fis = null;
                    Properties configProperties = null;
                    String configId = null;
                    try {
                        fis = new FileInputStream(configurationFile);
                        configProperties = new Properties();

                        try {
                            configProperties.loadFromXML(fis);
                            configId = configProperties.getProperty(Constants.PROPERTY_CONFIGURATION_ID);
                            if (dirName.startsWith("failed_expired_")) {
                                m_failedExpired_configurationDirectories.put(configId, dirName);
                                continue;
                            }
                            if (isMonitoringTimeOver(configProperties)) {
                                m_expiredConfigurationsSinceLastRun.add(configId);
                            }
                            this.m_configurations.put(configId, configProperties);
                            this.m_configurationDirectoriesPathes.put(configId, dirName);
                        }
                        catch (InvalidPropertiesFormatException e) {
                            String message =
                                "Can not restore the configuration data from the directory " + m_baseDir + "/"
                                    + dirName + " on start up of the Depositor:";
                            logger.error(message + e.getMessage());
                            continue;
                        }
                        catch (IOException e) {
                            String message =
                                "Can not restore the configuration from the directory " + m_baseDir + "/" + dirName
                                    + " on start up of the Depositor:";
                            logger.error(message + e.getMessage());
                            continue;
                        }

                    }
                    catch (FileNotFoundException e) {
                        String message =
                            "Can not restore the configuration data from the directory " + m_baseDir + "/" + dirName
                                + " on start up of the Depositor:";
                        logger.error(message + e.getMessage());
                        continue;
                    }
                    // save configuration directory from last run to
                    // process it later
                    m_dirsFromLastRunToProcess.put(configId, dirs[i]);
                }
                else {
                    dirs[i].delete();
                }
            }
        }
        m_threadNumber = 0;
        m_maxThreadNumber = maxThreadNumber;
        setName("Session-Reaper");
        // EscidocConnector.init();
        EscidocUtility.init(m_contentFileServletUrl);
        start();
    }

    /**
     * Method processes not successful content files, remained from a last run of a Depositor service.
     * 
     * @param directoryToProcess
     * @param configId
     */
    void processContentFiles(final File directoryToProcess, final String configId) {
        File[] files = directoryToProcess.listFiles();
        for (int j = 0; j < files.length; j++) {
            if (!(files[j].getName().equals(Constants.CONFIGURATION_FILE_NAME) || files[j].getName().startsWith(
                "successful_"))) {
                ItemSession session = null;
                try {
                    // storing a content file into
                    // infrastructure
                    session = new ItemSession(this, m_configurations.get(configId), files[j], directoryToProcess, null);
                    session.start();
                }
                catch (DepositorException e) {
                    // FIXME give a message
                    logger.error(e.getMessage(), e);
                    addToFailedConfigurations(configId);
                }
            }
        }
    }

    // ////////////////////////////////////////////////////////////////////////
    /**
     * Method increases counter holding the number of currently running threads.
     */
    protected synchronized void increaseThreadsNumber() {
        m_threadNumber++;
    }

    /**
     * Method decreases counter holding the number of currently running threads.
     */
    protected synchronized void decreaseThreadsNumber() {
        m_threadNumber--;
    }

    /**
     * Sessions administrator thread.
     */
    public void run() {

        while (!m_threadNeedsToFinish) {
            // processing content files from last run of a Deposit service,
            // happens only once after restart of the Deposit service
            Iterator<String> it = m_dirsFromLastRunToProcess.keySet().iterator();
            while (it.hasNext()) {
                String configId = it.next();
                File dirToProcess = m_dirsFromLastRunToProcess.get(configId);
                processContentFiles(dirToProcess, configId);
            }
            m_dirsFromLastRunToProcess = new HashMap<String, File>();
            // iterate thru configurations, find expired configurations -->
            // clean sessions, find
            // failed configurations --> try to repair failed configurations
            if (m_configurations.size() > 0) {
                synchronized (m_configurations) {
                    Set<String> configIds = m_configurations.keySet();
                    Iterator<String> iter = configIds.iterator();
                    Vector<String> containerIds = new Vector<String>();
                    while (iter.hasNext()) {
                        String configId = iter.next();
                        Properties configuration = m_configurations.get(configId);
                        // if the configuration was already expired to restart
                        // time of Depositor or if a monitoring time for the
                        // configuration is over, clean up sessions for the
                        // configuration
                        if (m_expiredConfigurationsSinceLastRun.contains(configId)
                            || isMonitoringTimeOver(configuration)) {
                            cleanupSessions(configId);
                            iter.remove();
                            m_expiredConfigurationsSinceLastRun.remove(configId);
                        }
                        else {
                            // monitoring time is not over, ping a container for
                            // the
                            // configuration

                            String containerId = configuration.getProperty(Constants.PROPERTY_EXPERIMENT_ID);
                            if (!containerIds.contains(containerId)) {
                                String handle = configuration.getProperty(Constants.PROPERTY_USER_HANDLE);
                                // ping alive for the container with the handle
                                // try {
                                // GetMethod get = EscidocConnector
                                // .pingContainer(
                                // configuration
                                // .getProperty(Constants.PROPERTY_INFRASTRUCTURE_ENDPOINT),
                                // containerId, handle);
                                // get.releaseConnection();
                                // } catch (ApplicationException e) {
                                // logger.error("Error while ping container with id "
                                // + containerId + e.getMessage());
                                // // notify the user and eSyncDemon
                                // } catch (InfrastructureException e) {
                                // logger.error("Error while ping container with id "
                                // + containerId + e.getMessage());
                                // // notify the user and eSyncDemon
                                // } catch (ConnectionException e) {
                                // logger.error("Error while ping container with id "
                                // + containerId + e.getMessage());
                                // // notify the user and eSyncDemon
                                // } catch (Throwable e) {
                                // logger.error("Unexpected error while ping container with id "
                                // + containerId + e.getMessage());
                                // // notify the user and eSyncDemon
                                // }
                                containerIds.add(containerId);
                            }

                            // try to store failed content files of failed
                            // configurations into infrastructure
                            if (m_failedConfigurations.containsKey(configId)) {
                                boolean stillFailed = false;
                                Vector<ItemSession> oldSessionsForConfiguration = new Vector<ItemSession>();
                                Vector<ItemSession> newSessionsForConfiguration = new Vector<ItemSession>();
                                Vector<ItemSession> sessionsForConfiguration = m_sessions.get(configId);
                                synchronized (sessionsForConfiguration) {
                                    for (int i = 0; i < sessionsForConfiguration.size(); i++) {
                                        // check if a session was repaired in
                                        // the
                                        // meantime
                                        ItemSession session = sessionsForConfiguration.get(i);
                                        if (session.isFinished() && session.isSessionFailed()) {
                                            // try try to store failed content
                                            // files
                                            // into infrastructure
                                            stillFailed = true;
                                            try {
                                                ItemSession newSession =
                                                    new ItemSession(this, configuration, session.get_contentFile(),
                                                        session.get_configurationDirectory(),
                                                        session.get_providedCheckSum());

                                                newSessionsForConfiguration.add(newSession);
                                                oldSessionsForConfiguration.add(session);
                                            }
                                            catch (DepositorException e) {
                                                // FIXME give a message
                                                logger.error(e.getMessage(), e);
                                            }
                                        }
                                    }
                                    sessionsForConfiguration.removeAll(oldSessionsForConfiguration);
                                }
                                for (int i = 0; i < newSessionsForConfiguration.size(); i++) {
                                    ItemSession session = newSessionsForConfiguration.get(i);
                                    session.start();
                                }
                                // if all currently finished sessions of the
                                // configuration was repaired
                                // in a meantime,
                                // the configuration is not 'failed' any more
                                // and
                                // deposit service can accept new
                                // content files for the configuration
                                if (!stillFailed) {
                                    synchronized (m_failedConfigurations) {
                                        m_failedConfigurations.remove(configId);
                                    }
                                }
                            }

                        }
                    }
                }
            }
            int waitedSeconds = 0;
            while (!m_threadNeedsToFinish && (waitedSeconds < m_pingInterval / 2)) {
                try {
                    Thread.sleep(1000);
                }
                catch (Exception e) {
                }
                waitedSeconds++;
            }
        }

        m_threadFinished = true;
    }

    /**
     * Method checks if the monitoring time of a provided configuration is over.
     * 
     * @param configuration
     * @return
     */
    private static boolean isMonitoringTimeOver(Properties configuration) {
        String monitoringStartTime = configuration.getProperty(Constants.PROPERTY_MONITORING_START_TIME);
        if (monitoringStartTime == null) {
            return false;
        }
        DateTimeZone.setDefault(DateTimeZone.UTC);

        DateTime startTime = new DateTime(monitoringStartTime);
        String monitoringDuration = configuration.getProperty(Constants.PROPERTY_TIME_MONITORING_DURATION);

        int monitoringDurationMinutes = Integer.parseInt(monitoringDuration);
        DateTime endTime = startTime.plusMinutes(monitoringDurationMinutes);

        return endTime.isBeforeNow();
    }

    // ////////////////////////////////////////////////////////////////////////

    /**
     * Method adds a session to the map of tracked sessions of a configuration with a provided id.
     */
    protected void addSession(ItemSession session, String configurationId) {
        synchronized (m_sessions) {
            Vector<ItemSession> configurationSessions = m_sessions.get(configurationId);
            if (configurationSessions == null) {
                configurationSessions = new Vector<ItemSession>();

            }
            configurationSessions.add(session);
            m_sessions.put(configurationId, configurationSessions);
        }
    }

    /**
     * Method adds a configuration to a map with failed configurations.
     * 
     * @param configurationId
     */
    public void addToFailedConfigurations(final String configurationId) {
        synchronized (m_failedConfigurations) {
            if (!m_failedConfigurations.containsKey(configurationId)) {
                m_failedConfigurations.put(configurationId, m_configurations.get(configurationId));
            }
        }
    }

    // ////////////////////////////////////////////////////////////////////////
    /**
     * Method checks if a limit of threads on Depositor is exceeded and calls a method to check a provided stream with a
     * configuration.
     */
    public void storeConfiguration(InputStream configurationStream) throws ApplicationException, DepositorException,
        ConnectionException, InfrastructureException {
        if (m_threadNumber == m_maxThreadNumber) {
            logger.error(ERR_MAX_THREADS_);
            throw new DepositorException(ERR_MAX_THREADS_);
        }

        logger.debug("Checking configuration");
        checkConfiguration(configurationStream);
    }

    /**
     * Method reads a configuration from a provided input stream. It checks if all mandatory configuration properties
     * are present and have valid values. It creates a directory for the configuration inside a base directory, save a
     * configuration file Constants.CONFIGURATION_FILE_NAME into this configuration directory and store a configuration
     * as an item in to an infrastructure with the infrastructure end point contained in the configuration.
     * 
     * @param configurationStream
     *            input stream containing a configuration
     * 
     * @throws DepositorException
     *             internal depositor error
     * @throws InfrastructureException
     *             internal infrastructure error
     * @throws ConnectionException
     *             if connection to the infrastructure failed
     * @throws ApplicationException
     *             if one of a configuration properties is missing or has invalid value
     */
    private void checkConfiguration(InputStream configurationStream) throws InfrastructureException,
        DepositorException, ConnectionException, ApplicationException {

        String configurationId;
        Configuration configProperties;

        try {

            configProperties = new Configuration();
            configProperties.loadFromXML(configurationStream);

            configurationId = configProperties.getProperty(Configuration.PROPERTY_CONFIGURATION_ID);
            logger.debug("Configuration ID: " + configurationId);

            if (configProperties.isValid()) {
                logger.debug("Config is valid.");
            }
            else {
                logger.debug("Config is invalid.");
            }

        }
        catch (InvalidPropertiesFormatException e) {
            logger.error(e.getMessage());
            throw new WrongFormatException(e.getMessage(), e);
        }
        catch (IOException e) {
            logger.error(e.getMessage());
            throw new DepositorException(e.getMessage(), e);
        }
        catch (NoSuchAlgorithmException e) {
            logger.error(e.getMessage());
            throw new DepositorException(e.getMessage(), e);
        }

        // check if configuration ID is already registered
        if (m_configurations.containsKey(configurationId)
            || m_expiredSuccessfulConfigurations.containsKey(configurationId)
            || m_failedExpired_configurationDirectories.containsKey(configurationId)) {
            String message =
                "The configuration with id " + configurationId + " is already registered by a Deposit service.";
            logger.error(message);
            throw new AlreadyExistException(message);
        }

        logger.debug("saving configuration");
        // save configuration
        File configFile = saveConfiguration(configProperties, configurationId);
        // get directory for this configuration created in saveConfiguration()
        String configDirName = m_configurationDirectoriesPathes.get(configurationId);

        logger.debug("prepare ingesting configuration");
        FileIngester ingester = null;
        ingester =
            new FileIngester(configProperties.getProperty(Configuration.PROPERTY_INFRASTRUCTURE_ENDPOINT),
                configProperties.getProperty(Configuration.PROPERTY_USER_HANDLE),
                configProperties.getProperty(Configuration.PROPERTY_EXPERIMENT_ID));

        System.out.println("should be the same:[");
        System.out.println(m_baseDir + "/" + configDirName + "/" + Constants.CONFIGURATION_FILE_NAME);
        System.out.println(configFile.getPath());
        System.out.println("]");

        ingester.addFile(m_baseDir + "/" + configDirName + "/" + Constants.CONFIGURATION_FILE_NAME);
        ingester.setItemContentModel(configProperties.getProperty(Configuration.PROPERTY_CONTENT_MODEL_ID));
        // FIXME
        ingester.setContainerContentModel(configProperties.getProperty(Configuration.PROPERTY_CONTENT_MODEL_ID));
        ingester.setContext(configProperties.getProperty(Configuration.PROPERTY_CONTEXT_ID));
        ingester.setContentCategory("ORIGINAL");
        ingester.setInitialLifecycleStatus(PublicStatus.PENDING); // ingester.getLifecycleStatus().get(0));
        ingester.setMimeType("text/xml"); // ingester.getMimeTypes().get(0));
        ingester.setValidStatus("valid");
        ingester.setVisibility("visible");

        try {
            logger.debug("ingesting configuration");
            ingester.setForceCreate(true);
            ingester.ingest();
            // FIXME
        }
        catch (ConfigurationException e) {
            logger.debug("ups", e);
            throw new DepositorException(e);
        }
        catch (IngestException e) {
            logger.debug("ups", e);
            throw new DepositorException(e);
        }
        catch (Throwable e) {
            logger.debug("ups", e);

        }

        synchronized (m_configurations) {
            logger.info("Successful saved a configuration with the id " + configurationId + " in the directory "
                + m_baseDir + "/" + configDirName);
            m_configurations.put(configProperties.getProperty(Configuration.PROPERTY_CONFIGURATION_ID),
                configProperties);
        }
    }

    /**
     * Removes a configuration file and configuration directory from a file system.
     * 
     * @param configFile
     */
    private void deleteConfigFile(File configFile) {
        String confDirectoryName = configFile.getParent();
        configFile.delete();
        File parentDirectory = new File(confDirectoryName);
        if (parentDirectory.exists()) {
            parentDirectory.delete();
        }
    }

    /**
     * Method creates a new configuration directory for a provided configuration with a currently time stamp as a name
     * and save a configuration file within it.
     * 
     * @param configuration
     * @param configurationId
     * @return File with a created configuration directory.
     * @throws DepositorException
     */
    private File saveConfiguration(Properties configuration, String configurationId) throws DepositorException {
        DateTimeZone.setDefault(DateTimeZone.UTC);
        DateTime currentTime = new DateTime();
        DateTimeFormatter fmt = DateTimeFormat.forPattern(PATH_FORMAT);
        String configurationDirectoryName = currentTime.toString(fmt);
        File configurationDirectory = new File(m_baseDir, configurationDirectoryName);
        configurationDirectory.mkdirs();
        File configurationFile = new File(configurationDirectory, Constants.CONFIGURATION_FILE_NAME);
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(configurationFile);
        }
        catch (FileNotFoundException e) {
            logger.error(e.getMessage());
            throw new DepositorException(e.getMessage());
        }
        try {
            configuration.storeToXML(os, null);
            os.flush();
            os.close();
            synchronized (m_configurationDirectoriesPathes) {
                m_configurationDirectoriesPathes.put(configurationId, configurationDirectoryName);
            }

        }
        catch (IOException e) {
            logger.error(e.getMessage());
            throw new DepositorException(e.getMessage());
        }
        return configurationFile;
    }

    /**
     * Method checks if a configuration-session is being cleaned at the moment by another thread. In this case it waits
     * before the other thread finished, reads the result of the clean operation and throws an exception if the clean
     * operation could not be successful performed. If a configuration-session is not being cleaned by another thread,
     * method calls a clean method cleanupSessions() and then removes a configuration from a map with registered
     * configurations.
     * 
     * @param configId
     * @throws ApplicationException
     * @throws DepositorException
     */
    public void deleteConfiguration(final String configId) throws ApplicationException, DepositorException {

        if (m_failedExpired_configurationDirectories.containsKey(configId)) {
            String message =
                "Can not delete the configuration: depositor could not store some content "
                    + "files for this configuration into the infrastracture.";
            logger.error(message);
            throw new DepositorException(message);
        }

        if (!m_isCleaning.contains(configId)) {
            synchronized (m_configurations) {
                if (!m_configurations.containsKey(configId)) {
                    String message = "Depositor can not find a configuration with the id " + configId + ".";
                    logger.error(message);
                    throw new ApplicationException(message);
                }
                // the configuration is not deleting at the moment by any
                // different thread and the configuration is registered,
                // cleanup session for the configuration
                cleanupSessions(configId);
                m_configurations.remove(configId);
            }
        }
        else {
            // wait until the thread cleaning this configuration is
            // finished
            while (m_isCleaning.contains(configId)) {
                try {
                    Thread.sleep(250);
                }
                catch (Exception e) {
                }
            }
        }
    }

    /**
     * Method calls a method putMonitoringStartTimeIntoConfigurationIfMissing() to check if a configuration with a
     * provided id contains a property Monitoring Start Time and put it into a configuration if it is missing. Then it
     * reads a provided input stream, checks if a provided check sum is valid and save a content into a configuration
     * directory for a configuration with a provided id. If a check sum is valid, it starts a new thread to store a
     * content as an item into a container in the infrastructure for the configuration.
     * 
     * @param configId
     * @param checkSumValue
     * @param in
     * @param fileName
     * @return true - if a check sum is valid, false - otherwise
     * @throws ApplicationException
     * @throws DepositorException
     */
    public boolean refactorNameOfThisMethod(
        final String configId, final String checkSumValue, final InputStream in, final String fileName)
        throws ApplicationException, DepositorException {

        checkPreconditions(configId);
        File configurationDirectory = new File(m_baseDir, m_configurationDirectoriesPathes.get(configId));
        checkIfExists(configId, configurationDirectory);
        checkFileName(configId, fileName, configurationDirectory);
        putMonitoringStartTimeIntoConfigurationIfMissing(configId);
        return compareChecksum(configId, checkSumValue, configurationDirectory, new File(configurationDirectory,
            fileName), storeFileAndCalculateChecksum(configId, in, new File(configurationDirectory, fileName)));
    }

    private static void checkIfExists(final String configId, File configurationDirectory) throws DepositorException {
        if (!configurationDirectory.exists()) {
            String message =
                "Error on Depositor: can not found a directory for the configuration with the id " + configId + ".";
            logger.error(message);
            throw new DepositorException(message);
        }
    }

    private MessageDigest storeFileAndCalculateChecksum(final String configId, final InputStream in, File contentFile)
        throws DepositorException {
        // store stream content in file named with filename while computing the
        // message digest
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(contentFile);
        }
        catch (FileNotFoundException e) {
            logger.error(e.getMessage());
            throw new DepositorException(e.getMessage());
        }
        MessageDigest md = getMessageDigest(configId);
        DigestInputStream din = new DigestInputStream(in, md);
        byte[] buf = new byte[5000];
        int len;
        try {
            while ((len = din.read(buf)) > 0) {
                os.write(buf, 0, len);
            }
            os.close();
            din.close();
        }
        catch (IOException e) {
            logger.error(e.getMessage());
            throw new DepositorException(e.getMessage());
        }
        return md;
    }

    private boolean compareChecksum(
        final String configId, final String checkSumValue, File configurationDirectory, File contentFile,
        MessageDigest md) throws DepositorException {

        // compare computed digest with the one send with the request
        byte[] digest = md.digest();
        String checksum = Utility.byteArraytoHexString(digest);
        logger.debug("checksum[" + checksum + "]");

        // now, content from the request is stored and validated.
        // create a session and start it. The session computed all additional
        // information and stores the content as component content in an item in
        // the eSciDoc Infrastructure.
        if (checksum.equals(checkSumValue)) {
            new ItemSession(this, m_configurations.get(configId), contentFile, configurationDirectory, checksum)
                .start();
            return true;
        }

        contentFile.delete();
        return false;
    }

    private MessageDigest getMessageDigest(final String configId) throws DepositorException {
        MessageDigest md = null;
        try {
            md =
                MessageDigest.getInstance(m_configurations.get(configId).getProperty(
                    Constants.PROPERTY_CHECKSUM_ALGORITHM));
        }
        catch (NoSuchAlgorithmException e) {
            logger.error(e.getMessage());
            throw new DepositorException(e.getMessage());
        }
        return md;
    }

    // check if filename is already sent for this configuration
    private static void checkFileName(final String configId, final String fileName, File configurationDirectory)
        throws AlreadyExistException {
        File[] files = configurationDirectory.listFiles();
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (name.endsWith(fileName)) {
                if (name.equals("successful_" + fileName) || name.equals("failed_" + fileName)
                    || name.equals("successful_failed_" + fileName) || name.equals(fileName)) {
                    String message =
                        "A content file '" + fileName + "' for the configuration with id " + configId
                            + " already exists on Depositor.";
                    logger.error(message);
                    throw new AlreadyExistException(message);
                }
            }
        }
    }

    private void checkPreconditions(final String configId) throws DepositorException, AlreadyExpiredException,
        ApplicationException {
        if (m_threadNumber == m_maxThreadNumber) {
            logger.error(ERR_MAX_THREADS_);
            throw new DepositorException(ERR_MAX_THREADS_);
        }

        if (m_expiredSuccessfulConfigurations.containsKey(configId)
            || m_expiredConfigurationsSinceLastRun.contains(configId) || m_isCleaning.contains(configId)) {
            String message = "A session for the configuration with " + configId + " is expired.";
            logger.error(message);
            throw new AlreadyExpiredException(message);
        }

        if (m_failedExpired_configurationDirectories.containsKey(configId)) {
            String message =
                "A configuration with id  "
                    + configId
                    + " is expiered and failed due to an internal failure on a deposit service or on an infrastructure.";
            logger.error(message);
            throw new DepositorException(message);
        }

        if (m_failedConfigurations.containsKey(configId)) {
            String message =
                "Error on Depositor: can not temporary accept content files for the configuration with the id "
                    + configId + " due to an internal failure on a deposit service or on an infrastructure.";
            logger.error(message);
            throw new DepositorException(message);
        }

        if (!m_configurations.containsKey(configId)) {
            String message = "Can not find a configuration with the id " + configId + ".";
            logger.error(message);
            throw new ApplicationException(message);
        }
    }

    /**
     * Method checks if a configuration with a provided id contains a property Monitoring Start Time. It put a time
     * stamp with a current time into a configuration contained in a map with configurations and in a configuration
     * file.
     * 
     * @param configId
     */
    private void putMonitoringStartTimeIntoConfigurationIfMissing(String configId) {
        File configurationDirectory = new File(m_baseDir, m_configurationDirectoriesPathes.get(configId));
        Properties configuration = null;
        // if a configuration does not contain a calculated monitoring start
        // time,
        // put a calculated monitoring start time in to the configuration and
        // store the
        // configuration into a configuration file
        synchronized (m_configurations) {
            configuration = m_configurations.get(configId);
            String monitoringStartTime = configuration.getProperty(Constants.PROPERTY_MONITORING_START_TIME);
            if (monitoringStartTime == null) {
                DateTimeZone.setDefault(DateTimeZone.UTC);
                DateTime currentTime = new DateTime();
                DateTimeFormatter fmt = DateTimeFormat.forPattern(DATE_TIME_FORMAT);
                String timeStamp = currentTime.toString(fmt);
                configuration.put(Constants.PROPERTY_MONITORING_START_TIME, timeStamp);

                File configurationFile = new File(configurationDirectory, Constants.CONFIGURATION_FILE_NAME);
                configurationFile.delete();
                FileOutputStream os = null;
                try {
                    os = new FileOutputStream(configurationFile);
                }
                catch (FileNotFoundException e) {
                    logger.error(e.getMessage());

                }
                try {
                    configuration.storeToXML(os, null);
                    if (os != null) {
                        os.flush();
                        os.close();
                    }
                }
                catch (IOException e) {
                    logger.error(e.getMessage());

                }
            }
        }
    }

    // ///////////////////////////////////////////////////////////////////////

    public void close() {
        m_threadNeedsToFinish = true;
        while (!m_threadFinished) {
            try {
                Thread.sleep(250);
            }
            catch (Exception e) {
            }
        }
    }

    public void finalize() {
        close();
    }

    /**
     * Method puts a provided configuration id into a set with configurations, which are being cleaned at the moment, to
     * prevent another threads from the cleaning of the configuration. It checks if all of the configuration sessions
     * finished successful or a configuration has no sessions and is not contained in a map with a failed
     * configurations. In this case it removes a configuration directory and a configuration file from a file system and
     * put the configuration id into a map with successful finished configurations. Otherwise it calls a method
     * renameConfigDirectoryToFailedExpired() to mark a configuration directory as expired and failed. A fact, that a
     * configuration has no sessions and is contained in a map with failed configurations means that content files of
     * the configuration could not be restored from a file system on restart of Demon service. Finally method removes
     * the configuration id from a set with configurations, which are being cleaned at the moment.
     * 
     * @param configurationId
     */
    public void cleanupSessions(final String configurationId) {
        // add the configuration to a set of configurations, which are being
        // cleaned at the moment
        synchronized (m_isCleaning) {
            m_isCleaning.add(configurationId);
        }
        Vector<ItemSession> sessionsForConfiguration = null;
        Properties configuration = m_configurations.get(configurationId);
        synchronized (m_sessions) {
            sessionsForConfiguration = m_sessions.get(configurationId);
            m_sessions.remove(configurationId);
        }
        if (sessionsForConfiguration != null) {
            boolean configurationFailed = false;
            Iterator<ItemSession> iter = sessionsForConfiguration.iterator();
            File configurationDirectory = null;
            // check if some sessions of the configuration failed
            while (iter.hasNext()) {
                ItemSession session = iter.next();
                configurationDirectory = session.get_configurationDirectory();
                while (!session.isFinished()) {
                    try {
                        Thread.sleep(250);
                    }
                    catch (Exception e) {
                    }
                }
                if (!session.isSessionFailed()) {
                    session.deleteContentFile();
                    iter.remove();
                }
                else {
                    configurationFailed = true;
                }

            }
            if (configurationFailed) {
                // some sessions of the configuration failed (some content files
                // could not be stored into an infrastructure)
                renameConfigDirectoryToFailedExpired(configurationDirectory, configurationId);
            }
            else {
                // all sessions of the configuration finished successful (all
                // content files were stored into an infrastructure)
                removeSuccessfulConfiguration(configurationDirectory, configurationId, configuration);
            }
        }
        else {
            // There are no sessions for the configuration
            String confDirectoryName = m_configurationDirectoriesPathes.get(configurationId);
            File configurationDirectory = new File(m_baseDir, confDirectoryName);
            if (configurationDirectory.exists()) {
                if (m_failedConfigurations.containsKey(configurationId)) {
                    // content files for the configuration could not be restored
                    // from a file system after restart of a Depositor
                    renameConfigDirectoryToFailedExpired(configurationDirectory, configurationId);
                }
                else {
                    removeSuccessfulConfiguration(configurationDirectory, configurationId, configuration);
                }
            }
        }
        synchronized (m_failedConfigurations) {
            m_failedConfigurations.remove(configurationId);
        }
        synchronized (m_isCleaning) {
            // remove the configuration from a set of configurations, which
            // are being cleaned at the moment
            m_isCleaning.remove(configurationId);
        }
    }

    /**
     * Method removes a provided configuration file and a provided configuration directory from a file system. It puts a
     * provided configuration id and a provided configuration into a map with successful finished configurations.
     * 
     * @param configurationFile
     * @param configurationDirectory
     * @param configurationId
     * @param configuration
     */
    private void removeSuccessfulConfiguration(
        final File configurationDirectory, final String configurationId, final Properties configuration) {
        File[] files = configurationDirectory.listFiles();
        for (int i = 0; i < files.length; i++) {
            files[i].delete();
        }
        configurationDirectory.delete();
        synchronized (m_expiredSuccessfulConfigurations) {
            m_expiredSuccessfulConfigurations.put(configurationId, configuration);
        }
    }

    /**
     * Method adds a a prefix 'failed_expired_' to a configuration directory name and put the configuration id into a
     * map, containing ids of failed expired configurations and configuration directory names.
     * 
     * @param configurationDirectory
     * @param configurationId
     */
    private void renameConfigDirectoryToFailedExpired(final File configurationDirectory, final String configurationId) {
        File[] files = configurationDirectory.listFiles();
        for (int i = 0; i < files.length; i++) {
            if (files[i].getName().startsWith("successful_")) {
                files[i].delete();
            }
        }
        String configDirName = configurationDirectory.getName();
        synchronized (m_failedExpired_configurationDirectories) {
            boolean success = configurationDirectory.renameTo(new File(m_baseDir, "failed_expired_" + configDirName));
            if (!success) {
                m_failedExpired_configurationDirectories.put(configurationId, configDirName);
                logger.error("Error while cleaning up sessions for the configuration with id " + configurationId
                    + " : can not rename a configuration directory to 'failed_expired_" + configDirName + "'.");
            }
            else {
                m_failedExpired_configurationDirectories.put(configurationId, "failed_expired_" + configDirName);
            }
        }
    }

}
