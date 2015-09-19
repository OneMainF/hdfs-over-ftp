## hdfs-over-ftp
FTP server based on [Apache FtpServer](http://mina.apache.org/ftpserver-project/) which works on a top of HDFS.

### Features
* Supports Apache FtpServer 1.0.6
* Active or passive connections
* SSL support
* Passwords are stored as MD5 hashes
* Users locked into home directory (configured per user)
* JMX for remote statistics viewing and reset
* Can run on YARN using [Apache Slider](http://slider.incubator.apache.org/) - see [hdfs-over-ftp-slider](https://github.com/jpbelleau/hdfs-over-ftp-slider)

### Requirements
* Java (tested with Java 7)
* Maven
* HDFS

### Configuration
* Set users in configuration/users.properties
* Set server options in configuration/hdfs-over-ftp.properties (see comments in file)
* Set log configuration in configuration/log4j.xml (set to console by default)

### Running
1. Change Java options in start-server.sh
2. Start server using start-server.sh (use --approot to define application root - include trailing slash)

### References
[hdfs-over-ftp](https://github.com/iponweb/hdfs-over-ftp)

### License
Apache License 2.0
