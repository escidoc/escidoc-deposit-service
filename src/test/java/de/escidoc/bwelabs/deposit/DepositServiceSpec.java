package de.escidoc.bwelabs.deposit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.escidoc.bwelabs.depositor.service.DepositorServlet;
import de.escidoc.bwelabs.depositor.utility.Utility;
import de.escidoc.core.client.Authentication;
import de.escidoc.core.client.exceptions.TransportException;
import de.escidoc.core.client.exceptions.application.security.AuthenticationException;

public class DepositServiceSpec {

    private static final Logger LOG = LoggerFactory.getLogger(DepositServiceSpec.class);

    private static final String SERVICE_URL = "http://escidev4.fiz-karlsruhe.de:8080/";

    private static final String CONFIGURATION_DEPOSIT_URI =
        "http://localhost:8086/deposit-service/depositor/configuration";

    private static final String CONTENT_DEPOSIT_URI = "http://localhost:8086/deposit-service/depositor";

    private static final String SYSADMIN = "sysadmin";

    private static final String SYSADMIN_PASSWORD = "eSciDoc";

    private static final String CONTENT_EXAMPLE = "header.txt";

    private static final int HOW_MANY = 2;

    @Ignore
    @SuppressWarnings("boxing")
    @Test
    public void shouldStoreConfigurationFile() throws Exception {
        // Given X0 && ...Xn
        Configuration configuration = createConfiguration();

        // When
        HttpResponse response = saveConfiguration(toXml(configuration));

        // Then ensure that
        int statusCode = response.getStatusLine().getStatusCode();

        LOG.debug("Status code: " + statusCode);
        assertSame("Saving configuration file failed...", 1, statusCode / 200);
    }

    @Ignore
    @SuppressWarnings("boxing")
    @Test
    public void shouldStoreContentAsItem() throws Exception {
        // Given X0 && ...Xn
        Configuration configuration = createConfiguration();
        String id = configuration.getProperty(Configuration.PROPERTY_CONFIGURATION_ID);

        // When
        if (isSavingSuccesful(configuration)) {
            HttpResponse response = saveContent(configuration, id, CONTENT_EXAMPLE);
            int statusCode = response.getStatusLine().getStatusCode();

            LOG.debug("Status code: " + statusCode);
            assertSame("Saving content failed", 1, statusCode / 200);
        }
    }

    @Ignore
    @SuppressWarnings("boxing")
    @Test
    public void shouldReturn409IfSameFileNameStoredMoreThanOnce() throws Exception {
        // Given X0 && ...Xn
        Configuration configuration = createConfiguration();
        String id = configuration.getProperty(Configuration.PROPERTY_CONFIGURATION_ID);

        // When
        if (isSavingSuccesful(configuration)) {
            for (int i = 0; i < HOW_MANY; i++) {
                HttpResponse response = saveContent(configuration, id, CONTENT_EXAMPLE);
                if (moreThanOne(i)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    LOG.debug("Status code: " + statusCode);
                    LOG.debug("Reason: " + response.getStatusLine().getReasonPhrase());
                    assertSame(
                        "Server does not response 409/Conflict, when the client send more than one files with the same name",
                        statusCode, 409);
                }
            }
        }
    }

    @Test
    public void shouldStoreMoreThanOneFiles() throws Exception {
        // Given X0 && ...Xn
        Configuration configuration = createConfiguration();
        String id = configuration.getProperty(Configuration.PROPERTY_CONFIGURATION_ID);

        // When
        if (isSavingSuccesful(configuration)) {
            for (int i = 0; i < HOW_MANY; i++) {
                Thread.sleep(3000);
                HttpResponse response = saveContent(configuration, id, CONTENT_EXAMPLE + new Date().getTime());
                int statusCode = response.getStatusLine().getStatusCode();
                LOG.debug("Status code: " + statusCode);
                LOG.debug("Reason: " + response.getStatusLine().getReasonPhrase());
                assertEquals("Can not save more than one files. ", 200, statusCode);
            }
        }
    }

    private boolean moreThanOne(int i) {
        return i > 0;
    }

    private HttpResponse saveContent(Configuration configuration, String id, String fileName)
        throws URISyntaxException, NoSuchAlgorithmException, IOException, ClientProtocolException {

        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(new URI(CONTENT_DEPOSIT_URI + "/" + id));
        setHeader(configuration, post, fileName);
        setEntity(post);
        return client.execute(post);
    }

    private void setEntity(HttpPost post) {
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(this.getClass().getClassLoader().getResourceAsStream(CONTENT_EXAMPLE));
        post.setEntity(entity);
    }

    private void setHeader(Configuration configuration, HttpPost post, String fileName)
        throws NoSuchAlgorithmException, IOException {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(CONTENT_EXAMPLE);
        post.setHeader(DepositorServlet.CONTENT_DISPOSITION_HEADER, fileName);
        String checksum = calculateCheckSum(configuration, is);
        post.setHeader(DepositorServlet.ESCIDOC_CHECKSUM_HEADER, checksum);
    }

    private boolean isSavingSuccesful(Configuration configuration) throws URISyntaxException, IOException,
        ClientProtocolException {
        return saveConfiguration(toXml(configuration)).getStatusLine().getStatusCode() / 200 == 1;
    }

    private String calculateCheckSum(Configuration conf, InputStream is) throws NoSuchAlgorithmException, IOException {
        MessageDigest instance = MessageDigest.getInstance(conf.getProperty(Configuration.PROPERTY_CHECKSUM_ALGORITHM));
        DigestInputStream digest = new DigestInputStream(is, instance);

        int read = digest.read();
        while (read != -1) {
            read = digest.read();
        }
        return Utility.byteArraytoHexString(instance.digest());
    }

    private Configuration createConfiguration() throws IOException, InvalidPropertiesFormatException,
        MalformedURLException, AuthenticationException, TransportException {

        Configuration example = loadConfiguration();
        Configuration adapted = edit(example);
        return adapted;

    }

    private OutputStream toXml(Properties adapted) throws IOException {
        OutputStream os = new ByteArrayOutputStream();
        adapted.storeToXML(os, "From functional testing.");
        return os;
    }

    private Configuration edit(Configuration toBeSent) throws AuthenticationException, TransportException,
        MalformedURLException {

        toBeSent.setProperty(Configuration.PROPERTY_USER_HANDLE, new Authentication(new URL(SERVICE_URL), SYSADMIN,
            SYSADMIN_PASSWORD).getHandle());

        String id = createId();

        toBeSent.setProperty(Configuration.PROPERTY_CONFIGURATION_ID, id);
        return toBeSent;
    }

    private String createId() {
        return "chh-" + Long.toString(new Date().getTime());
    }

    private HttpResponse saveConfiguration(OutputStream os) throws URISyntaxException, IOException,
        ClientProtocolException {
        LOG.debug("Configuration as String: " + os.toString());
        HttpClient client = new DefaultHttpClient();

        HttpPut put = new HttpPut(new URI(CONFIGURATION_DEPOSIT_URI));
        put.setEntity(new StringEntity(os.toString()));
        HttpResponse response = client.execute(put);
        return response;
    }

    private Configuration loadConfiguration() throws IOException, InvalidPropertiesFormatException {
        Configuration toBeSent = new Configuration();
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("bwelabs-configuration-example.xml");
        toBeSent.loadFromXML(is);
        return toBeSent;
    }

}