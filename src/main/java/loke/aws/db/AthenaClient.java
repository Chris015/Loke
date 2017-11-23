package loke.aws.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AthenaClient extends JdbcManager {
    private static final Logger log = LogManager.getLogger(AthenaClient.class);

    public AthenaClient(String theHost, Integer thePort, String theAwsAccessKey, String theAwsSecretKey,
                        String theStagingDir) {
        try {
            log.info("Initiate the Athena driver: {}, {}, {}, {}", theHost, thePort, theAwsAccessKey, theStagingDir);
            // Register db driver
            Class.forName("com.amazonaws.athena.jdbc.AthenaDriver");
            // Default port is 443
            if (thePort == null) {
                thePort = 443;
            }
            // Create jdbc url
            setUrl("jdbc:awsathena://" + theHost + ":" + thePort + "/");
            setProperty("user", theAwsAccessKey);
            setProperty("password", theAwsSecretKey);
            setProperty("s3_staging_dir", theStagingDir);
            setProperty("connection_timeout", "60000");
            setProperty("socket_timeout", "" + 2 * 60 * 60 * 1000);

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load DB driver", e);
        }
    }
}