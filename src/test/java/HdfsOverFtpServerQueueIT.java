/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hadoop.contrib.ftp.tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.HashMap;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.activemq.ActiveMQConnectionFactory;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import org.apache.hadoop.contrib.ftp.HdfsOverFtpServer;
import org.apache.hadoop.contrib.ftp.tests.utils.HdfsOverFtpServerTestUtils;

public class HdfsOverFtpServerQueueIT extends TestCase {

	private static final Logger log = LoggerFactory.getLogger(HdfsOverFtpServerQueueIT.class);

    private HdfsOverFtpServerTestUtils testUtils;
    private Map<String, String> subsConfigFileVals;
    private Map<String, String> subsAAMQConfFileVals;
    private int ftpClearPortCtl = 0;
    private int ftpClearPortPsv = 0;

    private int aamqOpenWirePort = 0;
    private String aamqQueueName = "";
    private String aamqDataDir = "";

    // Get back to the root of test-classes
    private final String appRootRelPrefix = "../../../../../../";
    private String appRoot = null;
    private String configFilesDir = "HdfsOverFtpServerQueueIT";

    // HDFS
    private String hdfsUri = "";

    public HdfsOverFtpServerQueueIT(String name) {
        super(name);
    }

    @Before
    public void setUp() throws Exception {
    	log.debug("Setup");

        final URL appRootURL = this.getClass().getResource(appRootRelPrefix + configFilesDir + "/");

        if (appRootURL != null) {
            appRoot = appRootURL.getPath();
        }

        if (appRootURL == null) {
            log.error("Application root is null");
            assertTrue(false);
        }

        log.debug("Application root:" + appRoot);

        testUtils = new HdfsOverFtpServerTestUtils();
        ftpClearPortCtl = testUtils.getFreeTCPPort();
        ftpClearPortPsv = testUtils.getFreeTCPPort();

        // Start the MiniDFS clutser and create the user directory
        hdfsUri = testUtils.createMiniDFSCluster(appRoot);
        testUtils.createHDFSPath("/user/testu");

        subsConfigFileVals = new HashMap<String, String>();
        subsConfigFileVals.put("port", "" + ftpClearPortCtl);
        subsConfigFileVals.put("data-ports", "" + ftpClearPortPsv);
        subsConfigFileVals.put("hdfs-uri", hdfsUri);
        subsConfigFileVals.put("hdfs-user", System.getProperty("user.name"));

        // Setup ActiveMQ server
        aamqOpenWirePort = testUtils.getFreeTCPPort();
        aamqQueueName = "testqueue";
        aamqDataDir = appRoot + "aamqdata";
        File aamqDataDirObj = new File(aamqDataDir);
        aamqDataDirObj.mkdir();

        subsAAMQConfFileVals = new HashMap<String, String>();
        subsAAMQConfFileVals.put("openwire-port", "" + aamqOpenWirePort);
        subsAAMQConfFileVals.put("queue-name", aamqQueueName);
        subsAAMQConfFileVals.put("activemq-data", aamqDataDir);
        testUtils.createActiveMQServer(appRoot, subsAAMQConfFileVals);

        // Add queue options to hdfs-over-ftp config
        subsConfigFileVals.put("queue-port", "" + aamqOpenWirePort);
        subsConfigFileVals.put("queue-name", aamqQueueName);
    }

    @Test
    public void testHdfsUser() {
    	log.debug("Test using HDFS user");
        FTPClient ftpClient = new FTPClient();
        int ftpClientReply = 0;

        try {
            testUtils.subsConfigFile(appRoot + "hdfs-over-ftp.properties.template", appRoot + "hdfs-over-ftp.properties", subsConfigFileVals);

            // Start the FTP server
        	HdfsOverFtpServer hdfsOverFtpServerObj = new HdfsOverFtpServer();
    		hdfsOverFtpServerObj.main(new String[] {"--approot", appRoot});

            // Setup the client
            FTPClientConfig ftpClientConfig = new FTPClientConfig();
            ftpClient.configure(ftpClientConfig);

            // Connect
            log.debug("Connecting to 127.0.0.1:" + ftpClearPortCtl);
            ftpClient.connect("127.0.0.1", ftpClearPortCtl);
            ftpClient.enterLocalPassiveMode();
            ftpClient.login("testu", "admin");
            ftpClientReply = ftpClient.getReplyCode();
            if (FTPReply.isPositiveCompletion(ftpClientReply) == false) {
                fail("Bad reply from FTP server connect / login " + ftpClientReply + " " + ftpClient.getReplyString());
                ftpClient.disconnect();
            }

            // Change to the root directory (should be chrooted to user home dir)
            ftpClient.changeWorkingDirectory("/");
            ftpClientReply = ftpClient.getReplyCode();
            if (FTPReply.isPositiveCompletion(ftpClientReply) == false) {
                fail("Bad reply from FTP server CWD " + ftpClientReply + " " + ftpClient.getReplyString());
            }

            log.debug("List files");
            FTPFile[] ftpFiles = ftpClient.listFiles();
            for (FTPFile file : ftpFiles) {
                log.debug(file.getName());
            }

            log.debug("Make directory " + "test");
            if (ftpClient.makeDirectory("test") == false) {
                fail("Cannot make directory " + "test" + " " + ftpClient.getReplyString());
            }

            log.debug("Remove directory " + "test");
            if (ftpClient.removeDirectory("test") == false) {
                fail("Cannot remove directory " + "test" + " " + ftpClient.getReplyString());
            }

            // Send a file
            String uplFilename = "hdfs-over-ftp.properties";
            log.debug("Sending " + appRoot + uplFilename);
            FileInputStream uplFile = new FileInputStream(appRoot + uplFilename);
            if (ftpClient.storeFile(uplFilename, uplFile) == false) {
                fail("Cannot upload file " + uplFilename + " " + ftpClient.getReplyString());
            }
            uplFile.close();

            // Grab the message from the queue
            log.debug("Connect to queue " + aamqQueueName + " at localhost:" + aamqOpenWirePort);
            ActiveMQConnectionFactory aamqConnFactory = new ActiveMQConnectionFactory("vm://localhost:" + aamqOpenWirePort);
            Connection aamqConn = aamqConnFactory.createConnection("user", "password");
            aamqConn.start();
            Session aamqSession = aamqConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination aamqDest = aamqSession.createQueue(aamqQueueName);
            MessageConsumer aamqConsum = aamqSession.createConsumer(aamqDest);
            Message aamqMsg = aamqConsum.receive(3000);

            if (aamqMsg == null) {
                fail("No message received");
            }
            else if (aamqMsg instanceof TextMessage == false) {
                fail("Received a message that was not text " + aamqMsg);
            }
            else {
                TextMessage aamqTxtMsg = (TextMessage)aamqMsg;
                String aamqText = aamqTxtMsg.getText();
                log.debug("Received queue text message " + aamqText);
                String filepathInQ = aamqText;

                // Remove the pipe
                if (aamqText.indexOf("|") > 0) {
                    String[] aamqTextParts = aamqText.split("\\|");
                    filepathInQ = aamqTextParts[1];
                }

                if (filepathInQ.equalsIgnoreCase("/" + uplFilename) == false) {
                    fail("Queue message (" + filepathInQ + ") did not match upload filename (" + "/" + uplFilename + ")");
                }
            }

            aamqConsum.close();
            aamqSession.close();
            aamqConn.close();

            // Get a file
            String dlFilename = "hdfs-over-ftp2.fromserver";
            log.debug("Getting " + appRoot + dlFilename);
            FileOutputStream dlFile = new FileOutputStream(new File(appRoot + dlFilename));
            if (ftpClient.retrieveFile(uplFilename, dlFile) == false) {
                fail("Cannot download file " + uplFilename + " / " + dlFilename + " " + ftpClient.getReplyString());
            }
            dlFile.close();

            log.debug("Delete file " + uplFilename);
            if (ftpClient.deleteFile(uplFilename) == false) {
                fail("Cannot remove file " + uplFilename + " " + ftpClient.getReplyString());
            }

            ftpClient.logout();
            ftpClient.disconnect();

            hdfsOverFtpServerObj.stopServers();

            assertTrue(true);
        }
        catch (Exception e) {
            fail(e.getMessage());
        }
        finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.disconnect();
                }
            }
            catch (IOException ioe) {
                // Ignore
            }
        }
    }

    @After
    public void tearDown() throws Exception {
    	log.debug("Tear down");
        testUtils.destroyActiveMQServer();
        testUtils.destroyMiniDFSCluster();
    }
}