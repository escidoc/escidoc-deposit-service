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
package de.escidoc.bwelabs.depositor.utility;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import de.escidoc.bwelabs.depositor.error.DepositorException;
import de.escidoc.bwelabs.depositor.service.Constants;
import de.escidoc.bwelabs.depositor.service.ContentFileServlet;

public class EscidocUtility {

    private static String _contentFileServletUrl;

    public static String baseDir;

    public static void init(final String contentFileServletUrl) throws DepositorException {
        _contentFileServletUrl = contentFileServletUrl;

        // FIXME code duplication from ContentFileServlet.init
        InputStream propStream = EscidocUtility.class.getResourceAsStream("/depositor.properties");
        if (propStream == null) {
            throw new DepositorException("Error loading configuration: /depositor.properties not found in classpath");
        }
        Properties props = new Properties();
        try {
            props.load(propStream);
        }
        catch (IOException e) {
            throw new DepositorException("Error loading properties from stream.", e);
        }
        String dir = props.getProperty(ContentFileServlet.PROP_BASEDIR);
        if (dir == null) {
            throw new DepositorException("Required property missing: " + ContentFileServlet.PROP_BASEDIR);
        }
        else {
            baseDir = dir;
        }
    }

    /**
     * Method builds an escidoc-item-xml from a provided last modification date, meta data, configuration, item href,
     * component href and a local path to a content file relative to a base directory of a Depositor service. It calls a
     * method cretaeComponentXml() to build a component-xml. If the last modification date and item href are not null,
     * the result-item will be used for update, otherwise - for create.
     * 
     * 
     * @param configuration
     * @param pathToContentFile
     * @param escidocMetadata
     * @param lastModDate
     * @param itemHef
     * @param componentHref
     * @return
     */
    // public static String createContentItemXml(
    // final Properties configuration, final String pathToContentFile, final String escidocMetadata,
    // final String lastModDate, final String itemHef, final String componentHref) {
    // String properties =
    // "<escidocItem:properties xmlns:prop=\"" + Constants.PROPERTIES_NS_URI + "\" xmlns:srel=\""
    // + Constants.STRUCTURAL_RELATIONS_NS_URI + "\"><srel:context xlink:href=\"/ir/context/"
    // + configuration.getProperty(Constants.PROPERTY_CONTEXT_ID) + "\" />"
    // + "<srel:content-model xlink:href=\"/cmm/content-model/"
    // + configuration.getProperty(Constants.PROPERTY_CONTENT_MODEL_ID) + "\" />"
    // + "</escidocItem:properties>";
    // String mdRecords =
    // "<escidocMetadataRecords:md-records xmlns:escidocMetadataRecords=\""
    // + Constants.METADATARECORDS_NAMESPACE_URI + "\"><escidocMetadataRecords:md-record name=\"escidoc\">"
    // + escidocMetadata + "</escidocMetadataRecords:md-record></escidocMetadataRecords:md-records>";
    // String itemStart =
    // "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<escidocItem:item xmlns:escidocItem=\""
    // + Constants.ITEM_NAMESPACE_URI + "\" xmlns:xlink=\"" + Constants.XLINK_NS_URI;
    // if (itemHef == null) {
    // itemStart = itemStart + "\">";
    // }
    // else {
    // itemStart = itemStart + "\" xlink:href=\"" + itemHef + "\" last-modification-date=\"" + lastModDate + "\">";
    // }
    //
    // String itemEnd = "</escidocItem:item>";
    // String item =
    // itemStart + properties + mdRecords + createComponentXml(pathToContentFile, componentHref) + itemEnd;
    //
    // return item;
    // }

    /**
     * Method builds an escidoc-item-xml from a provided configuration and a local path to a configuration file relative
     * to a base directory of a Depositor service. It calls a method cretaeComponentXml() to build a component-xml.
     * 
     * @param configuration
     * @param pathToConfigFile
     * @return
     */
    // public static String createConfigurationItemXml(final Properties configuration, final String pathToConfigFile) {
    // // FIXME Content Model ID must not be the one for content files. Get
    // // Content Model for configuration items from depositor.properties.
    // String properties =
    // "<escidocItem:properties xmlns:prop=\"" + Constants.PROPERTIES_NS_URI + "\" xmlns:srel=\""
    // + Constants.STRUCTURAL_RELATIONS_NS_URI + "\"><srel:context xlink:href=\"/ir/context/"
    // + configuration.getProperty(Constants.PROPERTY_CONTEXT_ID) + "\" />"
    // + "<srel:content-model xlink:href=\"/cmm/content-model/"
    // + configuration.getProperty(Constants.PROPERTY_CONTENT_MODEL_ID) + "\" />"
    // + "</escidocItem:properties>";
    // String mdRecords =
    // "<escidocMetadataRecords:md-records xmlns:escidocMetadataRecords=\""
    // + Constants.METADATARECORDS_NAMESPACE_URI + "\"><escidocMetadataRecords:md-record name=\"escidoc\">"
    // + "<oai_dc:dc xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
    // + "xmlns:oai_dc=\"http://www.openarchives.org/OAI/2.0/oai_dc/\"" + " >\n"
    // + "<dc:title>eLabs Experiment Configuration</dc:title>\n" + "<dc:identifier>"
    // + configuration.getProperty(Constants.PROPERTY_CONFIGURATION_ID) + "</dc:identifier></oai_dc:dc>\n"
    // + "</escidocMetadataRecords:md-record></escidocMetadataRecords:md-records>";
    // String itemStart =
    // "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<escidocItem:item xmlns:escidocItem=\""
    // + Constants.ITEM_NAMESPACE_URI + "\" xmlns:xlink=\"" + Constants.XLINK_NS_URI + "\">";
    // String itemEnd = "</escidocItem:item>";
    // String item = itemStart + properties + mdRecords + createComponentXml(pathToConfigFile, null) + itemEnd;
    //
    // return item;
    // }

    /**
     * Method builds a component xml from provided local path to a content file and component href. The local path to a
     * content file is relative to a base directory of Depositor service. Method builds an request-url to a
     * ContentFileServlet from the local path, in order to make a content file available for processes running outside
     * of the Depositor-service host, and set this url as a value of the attribute href of the component element
     * 'content'.
     * 
     * @param pathToFile
     * @param componentHref
     * @return
     */
    private static String createComponentXml(final String pathToFile, final String componentHref) {
        String properties =
            "<escidocComponents:properties xmlns:prop=\""
                + Constants.PROPERTIES_NS_URI
                + "\"><prop:visibility>public</prop:visibility><prop:content-category>experiment</prop:content-category><prop:mime-type>text/plain</prop:mime-type></escidocComponents:properties>";
        String content =
            "<escidocComponents:content xlink:href=\"" + _contentFileServletUrl
                + pathToFile.substring(baseDir.length()) + "\" storage=\"internal-managed\"/>";

        // FIXME errorhandling!?
        String mdRecords = "";
        if (!pathToFile.endsWith(Constants.CONFIGURATION_FILE_NAME)) {
            try {
                mdRecords =
                    "<escidocMetadataRecords:md-records xmlns:escidocMetadataRecords=\""
                        + Constants.METADATARECORDS_NAMESPACE_URI
                        + "\"><escidocMetadataRecords:md-record name=\"escidoc\">"
                        + EscidocUtility.getTechnicalMetadata(pathToFile)
                        + "</escidocMetadataRecords:md-record></escidocMetadataRecords:md-records>";
            }
            catch (DepositorException e) {
                // FIXME in which case there might be no metadata? enable
                // logging!
                System.out.println("Creation of technical metadata for component failed.");
                e.printStackTrace();
            }
        }

        String componentsStart =
            "<escidocComponents:components xmlns:escidocComponents=\"" + Constants.COMPONENTS_NAMESPACE_URI;
        if (componentHref == null) {
            componentsStart = componentsStart + "\"><escidocComponents:component>";
        }
        else {
            componentsStart = componentsStart + "\" xlink:href=\"" + componentHref + "\"><escidocComponents:component>";
        }
        String componentsEnd = "</escidocComponents:component></escidocComponents:components>";
        return componentsStart + properties + content + mdRecords + componentsEnd;
    }

    /**
     * Creates metadata describing the content of a file specified by local path. Should call ? Service.
     * 
     * @param pathToContentFile
     * @return
     */
    private static String getDescriptiveMetadata(final String pathToContentFile) {
        // FIXME call some metadata extractor
        // FIXME filename is not what send in disposition header if storing once
        // failed
        return "<oai_dc:dc xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
            + "xmlns:oai_dc=\"http://www.openarchives.org/OAI/2.0/oai_dc/\" >" + "<dc:title>" + pathToContentFile
            + "</dc:title>" + "<dc:description>depositor created item</dc:description>" + "</oai_dc:dc>";
    }

    /**
     * Creates technical metadata for a file specified by local path. Should call FITS Service.
     * 
     * @param pathToContentFile
     * @return
     * @throws DepositorException
     */
    private static String getTechnicalMetadata(final String pathToContentFile) throws DepositorException {
        // FIXME errorhandling undefined

        // FIXME path is system dependent
        String path = pathToContentFile;
        // path = path.replaceAll("/", "\\\\");
        String fitsServiceUrlString = "http://localhost:8080/TME/examine?path=" + path;
        String technicalMetadataXml = null;

        // try {
        // // HttpUtil hu = new HttpUtil();
        // // technicalMetadataXml = hu.getAsString(fitsServiceUrlString);
        //
        // } catch (IOException e) {
        // throw new DepositorException(e);
        // } catch (Exception e) {
        // // } catch (AuthenticationException e) {
        // throw new DepositorException(e);
        // }
        return technicalMetadataXml;
    }

}
