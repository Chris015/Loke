package loke;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import loke.config.Configuration;
import loke.config.YamlReader;
import loke.db.athena.AthenaClient;
import loke.email.AwsEmailSender;
import loke.email.AwsSesHandler;
import loke.email.Presenter;
import loke.model.User;

import java.util.ArrayList;
import java.util.List;

public class Loke {
    private ChartGenerator chartGenerator;
    private Presenter presenter;

    public Loke() {
        Configuration configuration = new YamlReader().readConfigFile("configuration.yaml");
        AthenaClient athenaClient =
                new AthenaClient(
                        configuration.getHost(),
                        configuration.getPort(),
                        configuration.getAccessKey(),
                        configuration.getSecretAccessKey(),
                        configuration.getStagingDir());
        HtmlTableCreator htmlTableCreator = new HtmlTableCreator();
        this.chartGenerator = new ChartGenerator(athenaClient, htmlTableCreator, configuration.getUserOwnerRegExp());
        AwsSesHandler awsSesHandler = new AwsSesHandler(AmazonSimpleEmailServiceClientBuilder.standard()
                .withRegion(configuration.getRegion())
                .withCredentials(
                        new AWSStaticCredentialsProvider(new BasicAWSCredentials(
                                configuration.getAccessKey(),
                                configuration.getSecretAccessKey())))
                .build());
        this.presenter = new AwsEmailSender(
                awsSesHandler,
                configuration.getFromEmailAddress(),
                configuration.getToEmailDomainName());
    }

    public void run() {
        List<User> users = chartGenerator.generateChartsOrderedByUser();
        presenter.present(new ArrayList<>());
    }
}
