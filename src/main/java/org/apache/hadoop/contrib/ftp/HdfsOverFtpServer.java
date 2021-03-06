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
package org.apache.hadoop.contrib.ftp;

import org.apache.ftpserver.impl.DefaultDataConnectionConfiguration;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.DataConnectionConfiguration;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.log4j.Logger;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Start-up class of FTP server
 */
public class HdfsOverFtpServer {

	private static Logger log = Logger.getLogger(HdfsOverFtpServer.class);

	private static int port = 0;
	private static int sslPort = 0;
	private static String passivePorts = null;
	private static String sslPassivePorts = null;
	private static String externalAddress = null;
	private static String sslExternalAddress = null;
	private static String mBeanName = "";
	private static String sslMBeanName = "";
	private static String sslKeystoreFile = "";
	private static String sslKeystorePassword = "";
	private static String hdfsUri = null;
	private static String hdfsUser = null;
	private static String hdfsGroup = null;
	private static boolean useVirtUserForCheck = true;
	private static boolean createMissingDirs = true;
	private static String appRoot = "";
	private static String jmxDomain = null;
	private static String discoveryMethod = "static";
	private static String appName = "";
	private static String compExportName = "";
	private static String queueHost = null;
	private static int queuePort = 0;
	private static String queueName = null;
	private static String queueUser = null;
	private static String queuePassword = null;

	private static FtpServer server = null;
	private static FtpServer sslServer = null;

	private static QueueManagerConfig queueMgrConfig = null;


	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new DefaultParser();
		Options options = new Options();
		options.addOption("h", "help", false, "WYSIWYG");
		options.addOption(OptionBuilder.withLongOpt("approot")
			.withDescription("use PATH - with trailing slash")
			.hasArg()
			.withArgName("PATH")
			.create());

		try {
			CommandLine line = parser.parse(options, args);
			if (line.hasOption("approot")) {
				appRoot = line.getOptionValue("approot");

				if (appRoot.endsWith("/") == false) {
					appRoot += "/";
				}
			}
			else if (line.hasOption("help")) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("HdfsOverFtpServer", options);
				System.exit(1);
			}
		}
		catch(Exception e) {
			log.fatal("Unexpected command line parse exception:" + e.getMessage());
			System.exit(1);
		}

		loadConfig();

		if (port != 0) {
			startServer();
		}

		if (sslPort != 0) {
			startSSLServer();
		}
	}

	/**
	 * Load configuration
	 *
	 * @throws IOException
	 */
	private static void loadConfig() throws IOException {
		Properties props = new Properties();
		//props.load(new FileInputStream(loadResource(appRoot + "hdfs-over-ftp.properties")));
		File propFile = new File(appRoot + "hdfs-over-ftp.properties");
		if (propFile.exists() == false) {
			throw new RuntimeException("Resource not found: " + appRoot + "hdfs-over-ftp.properties");
		}

		props.load(new FileInputStream(propFile));

		try {
			port = Integer.parseInt(props.getProperty("port"));
			log.info("port is set. ftp server will be started");
		} catch (Exception e) {
			log.info("port is not set. so ftp server will not be started");
		}

		try {
			sslPort = Integer.parseInt(props.getProperty("ssl-port"));
			log.info("ssl-port is set. ssl server will be started");
		} catch (Exception e) {
			log.info("ssl-port is not set. so ssl server will not be started");
		}

		if (port != 0) {
			mBeanName = props.getProperty("mbeanname");
			passivePorts = props.getProperty("data-ports");
			externalAddress = props.getProperty("external-address");
		}

		if (sslPort != 0) {
			sslMBeanName = props.getProperty("ssl-mbeanname");
			sslKeystoreFile = props.getProperty("ssl-keystore-file");
			sslKeystorePassword = props.getProperty("ssl-keystore-password");
			sslPassivePorts = props.getProperty("ssl-data-ports");
			sslExternalAddress = props.getProperty("ssl-external-address");

			if (sslKeystoreFile.startsWith("/") == false) {
				sslKeystoreFile = appRoot + sslKeystoreFile;
			}
		}

		hdfsUri = props.getProperty("hdfs-uri");
		if (hdfsUri == null) {
			log.fatal("hdfs-uri is not set");
			System.exit(1);
		}

		hdfsUser = props.getProperty("hdfsuser");
		if (hdfsUser == null) {
			hdfsUser = System.getProperty("user.name");
		}
		HdfsOverFtpSystem.setHDFSUser(hdfsUser);

		hdfsGroup = props.getProperty("hdfsgroup");
		if (hdfsGroup == null) {
			log.fatal("hdfsgroup is not set");
			System.exit(1);
		}
		HdfsOverFtpSystem.setHDFSGroup(hdfsGroup);

		try {
			useVirtUserForCheck = Boolean.parseBoolean(props.getProperty("usevirtuserforcheck"));
			log.info("virtual user for checking set - " + useVirtUserForCheck);
		} catch (Exception e) {
			useVirtUserForCheck = true;
			log.info("virtual user for checking not set.  will use HDFS user when checking permissions");
		}

		try {
			createMissingDirs = Boolean.parseBoolean(props.getProperty("createmissingdirs"));
			log.info("create missing directories set - " + createMissingDirs);
		} catch (Exception e) {
			createMissingDirs = false;
			log.info("create missing directories not set.  will not create missing directories");
		}

		jmxDomain = props.getProperty("jmxdomain");

		queueHost = props.getProperty("queuehost");
		if (queueHost == null) {
			log.info("queuehost is not set - not adding to a queue");
		}
		else {
			try {
				queuePort = Integer.parseInt(props.getProperty("queueport"));
			}
			catch (Exception e) {
				log.fatal("queueport must be set if queue host is set");
				System.exit(1);
			}

			queueName = props.getProperty("queuename");
			if (queueName == null) {
				log.fatal("queuename is not set");
				System.exit(1);
			}

			queueUser = props.getProperty("queueuser");
			if (queueUser == null) {
				log.info("queueuser is not set");
				queueUser = "";
			}

			queuePassword = props.getProperty("queuepassword");
			if (queuePassword == null) {
				log.info("queuepassword is not set");
				queuePassword = "";
			}

			if (queueHost.indexOf("~") > 0) {
				String[] queueHostParts = queueHost.split("~");
				discoveryMethod = queueHostParts[0];
				appName = queueHostParts[1];
				compExportName = queueHostParts[2];
			}

			queueMgrConfig = new QueueManagerConfig(appName, compExportName, discoveryMethod, queueHost, queuePort, queueName, queueUser, queuePassword);
		}
	}

	/**
	 * Starts FTP server
	 *
	 * @throws Exception
	 */
	public static void startServer() throws Exception {
		log.info("Starting Hdfs-Over-Ftp server - port: " + port + " hdfs-uri: " + hdfsUri + " usr: " + hdfsUser + " grp: " + hdfsGroup);

		HdfsOverFtpSystem.setHDFS_URI(hdfsUri);

		HdfsFtpServerFactory serverFactory = new HdfsFtpServerFactory();
		ListenerFactory listenFactory = new ListenerFactory();

		DataConnectionConfigurationFactory dataConFactory = new DataConnectionConfigurationFactory();

		if (passivePorts != null) {
			log.info("Passive ports: " + passivePorts + "");
			dataConFactory.setPassivePorts(passivePorts);
		}
		if (externalAddress != null) {
			log.info("External address: " + externalAddress + "");
			dataConFactory.setPassiveExternalAddress(externalAddress);
		}
		if ((passivePorts != null) || (externalAddress != null)) {
			log.info("Passive mode enabled");
		}
		dataConFactory.setActiveEnabled(true);

		DataConnectionConfiguration dataCon = dataConFactory.createDataConnectionConfiguration();
		listenFactory.setDataConnectionConfiguration(dataCon);
		listenFactory.setPort(port);
		serverFactory.addListener("default", listenFactory.createListener());

		File userFile = new File(appRoot + "users.properties");
		if (userFile.exists() == false) {
			throw new RuntimeException("Resource not found: " + appRoot + "users.properties");
		}

		HdfsUserManager userManager = new HdfsUserManager();
		userManager.setFile(userFile);
		userManager.configure();
		serverFactory.setUserManager(userManager);
		serverFactory.setFileSystem(new HdfsFileSystemFactory(useVirtUserForCheck, createMissingDirs));

		// Only create queue ftplet if queue params are available
		if (queueHost != null) {
			Map<String, Ftplet> ftpletMap = new HashMap<String, Ftplet>();
			HdfsFtpletQueue ftpletQueue = new HdfsFtpletQueue(queueMgrConfig);
			ftpletMap.put("ftpletQueue", ftpletQueue);
			serverFactory.setFtplets(ftpletMap);
		}

		server = serverFactory.createServer(jmxDomain, mBeanName);
		server.start();
	}

	/**
	 * Starts SSL FTP server
	 *
	 * @throws Exception
	 */
	public static void startSSLServer() throws Exception {
		log.info("Starting Hdfs-Over-Ftp SSL server - ssl-port: " + sslPort + " hdfs-uri: " + hdfsUri + " usr: " + hdfsUser + " grp: " + hdfsGroup);

		HdfsOverFtpSystem.setHDFS_URI(hdfsUri);

		HdfsFtpServerFactory serverFactory = new HdfsFtpServerFactory();
		ListenerFactory listenFactory = new ListenerFactory();

		DataConnectionConfigurationFactory dataConFactory = new DataConnectionConfigurationFactory();

		if (sslPassivePorts != null) {
			log.info("SSL passive ports: " + sslPassivePorts + "");
			dataConFactory.setPassivePorts(sslPassivePorts);
		}
		if (sslExternalAddress != null) {
			log.info("SSL external address: " + sslExternalAddress + "");
			dataConFactory.setPassiveExternalAddress(sslExternalAddress);
		}
		if ((sslPassivePorts != null) || (sslExternalAddress != null)) {
			log.info("SSL passive mode enabled");
		}
		dataConFactory.setActiveEnabled(true);

		DataConnectionConfiguration dataCon = dataConFactory.createDataConnectionConfiguration();
		listenFactory.setDataConnectionConfiguration(dataCon);
		listenFactory.setPort(sslPort);

		MySslConfiguration ssl = new MySslConfiguration();
		ssl.setKeystoreFile(new File(sslKeystoreFile));
		ssl.setKeystoreType("JKS");
		ssl.setKeyPassword(sslKeystorePassword);

		listenFactory.setSslConfiguration(ssl);
		listenFactory.setImplicitSsl(true);
		serverFactory.addListener("default", listenFactory.createListener());

		File userFile = new File(appRoot + "users.properties");
		if (userFile.exists() == false) {
			throw new RuntimeException("Resource not found: " + appRoot + "users.properties");
		}

		HdfsUserManager userManager = new HdfsUserManager();
		userManager.setFile(userFile);
		userManager.configure();
		serverFactory.setUserManager(userManager);
		serverFactory.setFileSystem(new HdfsFileSystemFactory(useVirtUserForCheck, createMissingDirs));

		// Only create queue ftplet if queue params are available
		if (queueHost != null) {
			Map<String, Ftplet> ftpletMap = new HashMap<String, Ftplet>();
			HdfsFtpletQueue ftpletQueue = new HdfsFtpletQueue(queueMgrConfig);
			ftpletMap.put("ftpletQueue", ftpletQueue);
			serverFactory.setFtplets(ftpletMap);
		}

		sslServer = serverFactory.createServer(jmxDomain, sslMBeanName);
		sslServer.start();
	}

	/**
	 * Stops FTP servers
	 *
	 * @throws Exception
	 */
	public static void stopServers() throws Exception {
		if (server != null) {
			stopServer();
		}

		if (sslServer != null) {
			stopSSLServer();
		}
		HdfsOverFtpSystem.unsetDfs();
	}

	/**
	 * Stops FTP server
	 *
	 * @throws Exception
	 */
	public static void stopServer() throws Exception {
		log.info("Stopping Hdfs-Over-Ftp server");
		server.stop();
	}

	/**
	 * Stops SSL FTP server
	 *
	 * @throws Exception
	 */
	public static void stopSSLServer() throws Exception {
		log.info("Stopping Hdfs-Over-Ftp SSL server");
		sslServer.stop();
	}
}
