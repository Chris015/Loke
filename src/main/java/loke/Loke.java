package loke;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import loke.config.AccountReader;
import loke.config.Configuration;
import loke.config.MalformedCSVException;
import loke.config.YamlReader;
import loke.db.athena.AthenaClient;
import loke.email.AwsEmailSender;
import loke.email.AwsSesHandler;
import loke.model.Employee;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Loke {
    private static final Logger log = LogManager.getLogger(Loke.class);
    private Configuration configuration;
    private AthenaClient athenaClient;
    private AccountReader accountReader;
    private CostReportGenerator costReportGenerator;
    private AwsEmailSender emailSender;

    public Loke() {
        this.configuration = new YamlReader().readConfigFile("configuration.yaml");
        this.athenaClient = new AthenaClient(
                configuration.getHost(),
                configuration.getPort(),
                configuration.getAccessKey(),
                configuration.getSecretAccessKey(),
                configuration.getStagingDir());

        setup();
    }

    /**
     * Used for injecting mocks when testing.
     *
     * @param configuration
     * @param athenaClient
     */
    public Loke(Configuration configuration, AthenaClient athenaClient) {
        this.configuration = configuration;
        this.athenaClient = athenaClient;
        setup();
    }

    private void setup() {
        this.accountReader = new AccountReader();
        Map<String, String> csvAccounts = readAccountsCsv("accounts.csv");

        this.costReportGenerator = new CostReportGenerator(athenaClient,
                configuration.getUserOwnerRegExp(),
                configuration.getGenerateReportThreshold(),
                csvAccounts);

        AwsSesHandler awsSesHandler = new AwsSesHandler(AmazonSimpleEmailServiceClientBuilder.standard()
                .withRegion(configuration.getRegion())
                .withCredentials(
                        new AWSStaticCredentialsProvider(new BasicAWSCredentials(
                                configuration.getAccessKey(),
                                configuration.getSecretAccessKey()))).build());

        this.emailSender = new AwsEmailSender(
                awsSesHandler,
                configuration.getFromEmailAddress(),
                configuration.getToEmailDomainName(),
                configuration.isDryRun());
    }

    private Map<String, String> readAccountsCsv(String filePath) {
        log.info("Loading accounts from: {}", filePath);
        Map<String, String> accounts = new HashMap<>();
        File csv = new File(filePath);
        if (csv.isFile()) {
            try {
                accounts = accountReader.readCSV("accounts.csv");
            } catch (MalformedCSVException e) {
                log.warn(e.getMessage());
                e.printStackTrace();
            }
        } else {
            log.info("No resource file found with path: " + filePath);
        }
        return accounts;
    }

    public void run() {

        List<Employee> employeeReports = null;
        List<Employee> adminReports;

        if(configuration.isSendOnlyAdminReport()){
            adminReports = costReportGenerator.generateAdminReports();
        } else {
            adminReports = costReportGenerator.generateAdminReports();
            employeeReports = costReportGenerator.generateReports();
        }

        if(employeeReports != null && employeeReports.size() > 0) {
            emailSender.sendEmployeeMails(employeeReports);
        }

        try {
            if (adminReports.size() > 0) {
                emailSender.sendAdminMails(configuration.getAdmins(), adminReports);
            }
        } catch (Exception e) {
            log.info(e);
        }
    }

    public void setEmailSender(AwsEmailSender emailSender) {
        this.emailSender = emailSender;
    }
}
