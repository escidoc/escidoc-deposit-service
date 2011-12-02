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

public final class Constants {

    public static final String CONFIGURATION_FILE_NAME = "configuration.xml";

    /**
     * Configuration properties.
     * 
     * 
     */
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

    /**
     * Schemas Namespace-URIs.
     */

    public static final String NS_URI_PREFIX = "http://www.escidoc.de/schemas/";

    public static final String NS_URI_SCHEMA_VERSION_0_1 = "/0.1";

    public static final String NS_URI_SCHEMA_VERSION_0_3 = "/0.3";

    public static final String NS_URI_SCHEMA_VERSION_0_4 = "/0.4";

    public static final String NS_URI_SCHEMA_VERSION_0_5 = "/0.5";

    public static final String NS_URI_SCHEMA_VERSION_0_6 = "/0.6";

    public static final String NS_URI_SCHEMA_VERSION_0_7 = "/0.7";

    public static final String NS_URI_SCHEMA_VERSION_0_8 = "/0.8";

    public static final String NS_URI_SCHEMA_VERSION_0_9 = "/0.9";

    /*
     * Current schema versions per resource
     */

    public static final String ITEM_NS_URI_SCHEMA_VERSION = NS_URI_SCHEMA_VERSION_0_9;

    public static final String CONTAINER_NS_URI_SCHEMA_VERSION = NS_URI_SCHEMA_VERSION_0_8;

    public static final String METADATARECORDS_NAMESPACE_URI = NS_URI_PREFIX + "metadatarecords"
        + NS_URI_SCHEMA_VERSION_0_5;

    public static final String COMPONENTS_NAMESPACE_URI = NS_URI_PREFIX + "components" + NS_URI_SCHEMA_VERSION_0_9;

    /*
     * END Current schema versions per resource
     */

    /**
     * Namespace-URIs.
     * 
     * 
     */

    public static final String PROPERTIES_NS_URI = "http://escidoc.de/core/01/properties/";

    public static final String STRUCTURAL_RELATIONS_NS_URI = "http://escidoc.de/core/01/structural-relations/";

    public static final String XLINK_NS_URI = "http://www.w3.org/1999/xlink";

    public static final String XML_NS_URI = "http://www.w3.org/XML/1998/namespace";

    public static final String ITEM_NAMESPACE_URI = NS_URI_PREFIX + "item" + ITEM_NS_URI_SCHEMA_VERSION;

    public static final String CONTAINER_NAMESPACE_URI = NS_URI_PREFIX + "container" + CONTAINER_NS_URI_SCHEMA_VERSION;

    public static final String ITEM_PROPERTIES_NAMESPACE_URI = ITEM_NAMESPACE_URI;

    public static final String XLINK_URI = XLINK_NS_URI;

}
