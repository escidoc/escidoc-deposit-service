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
package de.escidoc.bwelabs.deposit;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.escidoc.bwelabs.depositor.error.MissingConfigurationPropertyException;
import de.escidoc.bwelabs.depositor.service.Constants;

@SuppressWarnings("serial")
public class Configuration extends Properties {

    private static final Logger LOG = LoggerFactory.getLogger(Configuration.class);

    public static final String PROPERTY_CONFIGURATION_ID = "ConfigurationID";

    public static final String PROPERTY_USER_HANDLE = "UserHandle";

    public static final String PROPERTY_CONTENT_MODEL_ID = "ContentModelID";

    public static final String PROPERTY_CONTEXT_ID = "WorkspaceID";

    public static final String PROPERTY_EXPERIMENT_ID = "ExperimentID";

    public static final String PROPERTY_USER_EMAIL_ADRESS = "userEmailAdress";

    public static final String PROPERTY_DEPOSIT_SERVER_ENDPOINT = "DepositServerEndpoint";

    public static final String PROPERTY_INFRASTRUCTURE_ENDPOINT = "InfrastructureEndpoint";

    public static final String PROPERTY_E_SYNC_DEMON_ENDPOINT = "eSyncDemonEndpoint";

    public static final String PROPERTY_MONITORING_START_TIME = "MonitoringStartTime";

    public static final String PROPERTY_TIME_MONITORING_DURATION = "MonitoringDuration";

    public static final String PROPERTY_CHECKSUM_ALGORITHM = "CheckSumType";

    private boolean isValid;

    public boolean isValid() throws MissingConfigurationPropertyException, MalformedURLException,
        NoSuchAlgorithmException {
        // be optimistic
        this.isValid = true;

        if (isNullOrEmpty(this.getProperty(PROPERTY_CONFIGURATION_ID))) {
            String message = Constants.PROPERTY_CONFIGURATION_ID + " is missing.";
            LOG.error(message);
            this.isValid = false;
        }

        if (isNullOrEmpty(this.getProperty(PROPERTY_EXPERIMENT_ID))) {
            String message = Constants.PROPERTY_EXPERIMENT_ID + " is missing.";
            LOG.error(message);
            this.isValid = false;
        }

        if (isNullOrEmpty(this.getProperty(PROPERTY_INFRASTRUCTURE_ENDPOINT))) {
            String message = Constants.PROPERTY_INFRASTRUCTURE_ENDPOINT + " is missing.";
            LOG.error(message);
            this.isValid = false;
        }
        else {
            new URL(this.getProperty(PROPERTY_INFRASTRUCTURE_ENDPOINT));
        }

        if (isNullOrEmpty(this.getProperty(PROPERTY_USER_HANDLE))) {
            String message = Constants.PROPERTY_USER_HANDLE + " is missing.";
            LOG.error(message);
            this.isValid = false;
        }

        if (isNullOrEmpty(this.getProperty(PROPERTY_CONTEXT_ID))) {
            String message = Constants.PROPERTY_CONTEXT_ID + " is missing.";
            LOG.error(message);
            this.isValid = false;
        }

        if (isNullOrEmpty(this.getProperty(PROPERTY_CONTENT_MODEL_ID))) {
            String message = Constants.PROPERTY_CONTENT_MODEL_ID + " is missing.";
            LOG.error(message);
            this.isValid = false;
        }

        // PROPERTY_MONITORING_START_TIME is optional
        if (!isNullOrEmpty(this.getProperty(PROPERTY_MONITORING_START_TIME))) {
            // String message = Constants.PROPERTY_MONITORING_START_TIME
            // + " is missing.";
            // LOG.error(message);
            // this.isValid = false;
            // } else {
            // try {
            new DateTime(this.getProperty(PROPERTY_MONITORING_START_TIME));
            // } catch (IllegalArgumentException e) {
            // String message = "The value '" + monitoringStartTime
            // + "' of the property "
            // + Constants.PROPERTY_MONITORING_START_TIME
            // + " has a wrong format. ";
            // LOG.error(message);
            // this.isValid = false
            // }
        }

        // PROPERTY_TIME_MONITORING_DURATION is optional
        // FIXME is it?
        if (!isNullOrEmpty(this.getProperty(PROPERTY_TIME_MONITORING_DURATION))) {
            Integer.parseInt(this.getProperty(PROPERTY_TIME_MONITORING_DURATION));
            // if (isMonitoringTimeOver(configProperties)) {
            // String message = "The configuration is expired. ";
            // logger.error(message);
            // throw new WrongConfigurationContentException(message);
            // }
        }

        if (isNullOrEmpty(this.getProperty(PROPERTY_CHECKSUM_ALGORITHM))) {
            String message = Constants.PROPERTY_CHECKSUM_ALGORITHM + " is missing.";
            LOG.error(message);
            this.isValid = false;
        }
        else {
            MessageDigest.getInstance(this.getProperty(PROPERTY_CHECKSUM_ALGORITHM));
        }

        if (!this.isValid) {
            throw new MissingConfigurationPropertyException("Some properties are missing in the configuration.");
        }

        return true;
    }

    private boolean isNullOrEmpty(Object o) {
        if (o != null) {
            if (o instanceof String) {
                String s = (String) o;
                return s.isEmpty();
            }
            return false;
        }
        return true;
    }
}