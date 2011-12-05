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

/**
 * Utility class.
 * 
 * @author ROF
 * 
 */
public class Utility {
    public static String byteArraytoHexString(byte[] array) {
        final String hexByte = "0123456789abcdef";
        StringBuffer buf = new StringBuffer();
        for (byte val : array) {
            int v1 = val >>> 4 & 0x0f;
            int v2 = val & 0x0f;
            buf.append(hexByte.substring(v1, v1 + 1)).append(hexByte.substring(v2, v2 + 1));
        }
        return buf.toString();

    }

    /**
     * Fetches the id from the link. It is the String after the last '/' in the link.
     * 
     * @param link
     *            The link
     * @return The extracted id.
     */
    // public static String getId(final String link) {
    // String result = link;
    // int index = link.lastIndexOf("/");
    // if (index != -1) {
    // result = link.substring(link.lastIndexOf("/") + 1);
    // }
    // return result;
    // }
}
