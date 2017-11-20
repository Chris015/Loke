package loke.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

public class YamlReader implements ConfigReader {
    private static final Logger log = LogManager.getLogger(YamlReader.class);
    private ObjectMapper objectMapper;

    public YamlReader() {
        this.objectMapper = new ObjectMapper(new YAMLFactory());
    }

    @Override
    public Configuration readConfigFile(String filePath) {
        log.info("Reading configuration file");
        File configurationFile = new File(filePath);
        try {
            Configuration configuration = objectMapper.readValue(
                    configurationFile,
                    Configuration.class
            );
            log.info("Done reading configuration file");
            return configuration;
        } catch (IOException e) {
            log.error(e);
            e.printStackTrace();
        }
        log.error("Unable to read config file");
        throw new NullPointerException("Unable to read config file");
    }


}
