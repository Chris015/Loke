package loke;

import loke.config.Configuration;
import loke.aws.db.AthenaClient;
import loke.email.AwsEmailSender;
import loke.email.AwsSesHandler;
import loke.model.Admin;
import loke.utils.CalendarGenerator;
import loke.utils.ResourceLoader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static loke.aws.db.JdbcManager.QueryResult;
import static loke.service.ResourceStartedLastWeek.ResourceStartedLastWeekDao;
import static loke.service.SpendPerEmployeeByAccount.SpendPerEmployeeAndAccountDao;
import static loke.service.SpendPerEmployeeByResource.SpendPerEmployeeByResourceDao;
import static loke.service.TotalSpendPerEmployee.TotalSpendPerEmployeeDao;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class LokeIT {

    private static final String EMPLOYEE_BY_ACCOUNT_SQL = loadSql("SpendPerEmployeeByAccount.sql");
    private static final String LAST_WEEK_SQL = loadSql("ResourceStartedLastWeek.sql");
    private static final String EMPLOYEE_BY_RESOURCE_SQL = loadSql("SpendPerEmployeeByResource.sql");
    private static final String TOTAL_SPEND_SQL = loadSql("TotalSpendPerEmployee.sql");
    private static final String SENDER = "sender@sender.com";

    private Loke loke;
    private AwsEmailSender emailSender;
    private AthenaClient athenaClient;
    private AwsSesHandler awsSesHanlder;
    private Configuration configuration;
    private Admin admin;
    private Clock clock;

    private static String loadSql(String fileName) {
        return ResourceLoader.getResource("sql/" + fileName);
    }

    @Before
    public void setUp() throws Exception {
        // A custom clock is set to fake the current date
        this.clock = mock(Clock.class);
        CalendarGenerator.clock = clock;
        when(clock.instant()).thenReturn(Instant.parse("2017-09-05T00:00:00Z"));

        // AwsSES and Db client is mocked
        this.awsSesHanlder = mock(AwsSesHandler.class);
        this.athenaClient = mock(AthenaClient.class);

        this.emailSender = spy(new AwsEmailSender(awsSesHanlder, SENDER, "@domain.com", false));

        // Custom configuration is injected
        this.admin = new Admin("admin@domain.com");
        this.configuration = new Configuration();
        configuration.setAdmins(Arrays.asList(this.admin));
        configuration.setAccessKey("");
        configuration.setSecretAccessKey("");
        configuration.setUserOwnerRegExp("^([a-z]+\\.[a-z]+)+$");
        configuration.setSendOnlyAdminReport(false);

        this.loke = new Loke(configuration, athenaClient);
        loke.setEmailSender(emailSender);

        // If not otherwise specified in the test, the aws client will always return an empty list
        QueryResult queryResult = new QueryResult();
        queryResult.setResultList(new ArrayList());
        when(athenaClient.executeQuery(anyString(), (Class<Object>) any())).thenReturn(queryResult);
    }

    @Test
    public void sendOnlyAdminReportTrue_onlySendsReportToAdmin() throws Exception {
        // given
        configuration.setSendOnlyAdminReport(true);

        QueryResult queryResult = new QueryResult();
        queryResult.setResultList(createSpendPerEmployeeByAccountData());
        when(athenaClient.executeQuery(EMPLOYEE_BY_ACCOUNT_SQL, SpendPerEmployeeAndAccountDao.class)).thenReturn(queryResult);

        Loke loke = new Loke(configuration, athenaClient);
        loke.setEmailSender(emailSender);

        // when
        loke.run();

        // then
        verify(emailSender, times(1)).sendAdminMails(anyList(), anyList());
        verify(emailSender, times(0)).sendEmployeeMails(anyList());
    }

    @Test
    public void canGenerateEmailsFor_ResourcesStarterLastWeek() throws Exception {
        // Given
        QueryResult queryResult = new QueryResult();
        queryResult.setResultList(createResourceStartedLastWeekData());

        when(athenaClient.executeQuery(LAST_WEEK_SQL, ResourceStartedLastWeekDao.class)).thenReturn(queryResult);

        // When
        loke.run();

        // Then
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlBody = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> from = ArgumentCaptor.forClass(String.class);

        // Emails sent to Jane and John
        verify(awsSesHanlder, times(2)).sendEmail(to.capture(), htmlBody.capture(), anyString(), from.capture());
        List<String> values = to.getAllValues();
        assertEquals("jane.doe@domain.com", values.get(0));
        assertEquals("john.doe@domain.com", values.get(1));

        // Make sure the email bodies aren't empty
        List<String> htmlBodies = htmlBody.getAllValues();
        assertTrue(htmlBodies.get(0).length() > 0);
        assertTrue(htmlBodies.get(1).length() > 0);

        // No admin emails should be sent
        verify(emailSender, times(0)).sendAdminMails(anyList(), anyList());

        assertEquals(SENDER, from.getValue());
    }

    @Test
    public void canGenerateEmailsFor_SpendPerEmployeeByAccount() throws Exception {
        // Given
        QueryResult queryResult = new QueryResult();
        queryResult.setResultList(createSpendPerEmployeeByAccountData());

        when(athenaClient.executeQuery(EMPLOYEE_BY_ACCOUNT_SQL, SpendPerEmployeeAndAccountDao.class)).thenReturn(queryResult);

        // When
        loke.run();

        // Then
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlBody = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> from = ArgumentCaptor.forClass(String.class);

        // Three emails are sent
        verify(awsSesHanlder, times(3)).sendEmail(to.capture(), htmlBody.capture(), anyString(), from.capture());

        // Emails sent to Jane and John
        List<String> values = to.getAllValues();
        assertEquals("jane.doe@domain.com", values.get(0));
        assertEquals("john.doe@domain.com", values.get(1));

        // Email is sent to admin
        assertEquals(admin.getEmailAddress(), values.get(2));

        // Make sure the email bodies aren't empty
        List<String> htmlBodies = htmlBody.getAllValues();
        assertTrue(htmlBodies.get(0).length() > 0);
        assertTrue(htmlBodies.get(1).length() > 0);
        assertTrue(htmlBodies.get(2).length() > 0);

        assertEquals(SENDER, from.getValue());
    }

    @Test
    public void canGenerateEmailsFor_SpendPerEmployeeByResource() throws Exception {
        // Given
        QueryResult queryResult = new QueryResult();
        queryResult.setResultList(createSpendPerEmployeeByResourceData());

        when(athenaClient.executeQuery(EMPLOYEE_BY_RESOURCE_SQL, SpendPerEmployeeByResourceDao.class)).thenReturn(queryResult);

        // When
        loke.run();

        // Then
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlBody = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> from = ArgumentCaptor.forClass(String.class);

        // Two emails are sent
        verify(awsSesHanlder, times(2)).sendEmail(to.capture(), htmlBody.capture(), anyString(), from.capture());

        // Emails sent to Jane and John
        List<String> values = to.getAllValues();
        assertEquals("jane.doe@domain.com", values.get(0));
        assertEquals("john.doe@domain.com", values.get(1));

        // No admin emails should be sent
        verify(emailSender, times(0)).sendAdminMails(anyList(), anyList());

        // Make sure the email bodies aren't empty
        List<String> htmlBodies = htmlBody.getAllValues();
        assertTrue(htmlBodies.get(0).length() > 0);
        assertTrue(htmlBodies.get(1).length() > 0);

        assertEquals(SENDER, from.getValue());
    }

    @Test
    public void canGenerateEmailsFor_TotalSpendPerEmployee() throws Exception {
        // Given
        QueryResult queryResult = new QueryResult<>();
        queryResult.setResultList(createTotalSpendPerEmployeeData());

        when(athenaClient.executeQuery(TOTAL_SPEND_SQL, TotalSpendPerEmployeeDao.class)).thenReturn(queryResult);

        // When
        loke.run();

        // Then
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlBody = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> from = ArgumentCaptor.forClass(String.class);

        // One email is sent
        verify(awsSesHanlder, times(1)).sendEmail(to.capture(), htmlBody.capture(), anyString(), from.capture());

        // No emails are sent to employees
        verify(emailSender, times(0)).sendEmployeeMails(anyList());

        // One email is sent to admin
        verify(emailSender, times(1)).sendAdminMails(anyList(), anyList());

        //Make sure the email body isn't empty
        assertTrue(htmlBody.getValue().length() > 0);

        assertEquals(SENDER, from.getValue());

    }

    private List createTotalSpendPerEmployeeData() {
        List<TotalSpendPerEmployeeDao> totalSpendPerEmployeeDaos = new ArrayList<>();
        TotalSpendPerEmployeeDao dao;

        dao = new TotalSpendPerEmployeeDao();
        dao.userOwner = "john.doe";
        dao.startDate = "2017-09-01 09:00:00";
        dao.cost = 1500;
        totalSpendPerEmployeeDaos.add(dao);

        dao = new TotalSpendPerEmployeeDao();
        dao.userOwner = "jane.doe";
        dao.startDate = "2017-09-02 09:00:00";
        dao.cost = 750;
        totalSpendPerEmployeeDaos.add(dao);

        return totalSpendPerEmployeeDaos;
    }

    private List createSpendPerEmployeeByResourceData() {
        List<SpendPerEmployeeByResourceDao> spendPerEmployeeByResourceDaos = new ArrayList<>();
        SpendPerEmployeeByResourceDao dao;

        dao = new SpendPerEmployeeByResourceDao();
        dao.userOwner = "john.doe";
        dao.productName = "Ec2";
        dao.startDate = "2017-09-01 09:00:00";
        dao.cost = 125;
        spendPerEmployeeByResourceDaos.add(dao);

        dao = new SpendPerEmployeeByResourceDao();
        dao.userOwner = "john.doe";
        dao.productName = "S3";
        dao.startDate = "2017-09-01 09:00:00";
        dao.cost = 75;
        spendPerEmployeeByResourceDaos.add(dao);

        dao = new SpendPerEmployeeByResourceDao();
        dao.userOwner = "jane.doe";
        dao.productName = "Ec2";
        dao.startDate = "2017-09-02 09:00:00";
        dao.cost = 150;
        spendPerEmployeeByResourceDaos.add(dao);

        return spendPerEmployeeByResourceDaos;
    }

    private List<SpendPerEmployeeAndAccountDao> createSpendPerEmployeeByAccountData() {
        List<SpendPerEmployeeAndAccountDao> spendPerEmployeeAndAccountDaos = new ArrayList<>();
        SpendPerEmployeeAndAccountDao dao;

        dao = new SpendPerEmployeeAndAccountDao();
        dao.userOwner = "john.doe";
        dao.accountId = "QA";
        dao.productName = "Ec2";
        dao.startDate = "2017-09-01 09:00:00";
        dao.cost = 100;
        spendPerEmployeeAndAccountDaos.add(dao);

        dao = new SpendPerEmployeeAndAccountDao();
        dao.userOwner = "jane.doe";
        dao.accountId = "Nova";
        dao.productName = "S3";
        dao.startDate = "2017-09-02 09:00:00";
        dao.cost = 50;
        spendPerEmployeeAndAccountDaos.add(dao);

        return spendPerEmployeeAndAccountDaos;
    }

    private List<ResourceStartedLastWeekDao> createResourceStartedLastWeekData() {
        List<ResourceStartedLastWeekDao> resourceStartedLastWeekDaos = new ArrayList<>();
        ResourceStartedLastWeekDao dao;

        dao = new ResourceStartedLastWeekDao();
        dao.userOwner = "john.doe";
        dao.accountId = "QA";
        dao.resourceId = "i-123456";
        dao.startDate = "2017-09-01 09:00:00";
        dao.cost = 100;
        resourceStartedLastWeekDaos.add(dao);

        dao = new ResourceStartedLastWeekDao();
        dao.userOwner = "john.doe";
        dao.accountId = "QA";
        dao.resourceId = "v-654321";
        dao.startDate = "2017-09-02 09:00:00";
        dao.cost = 50;
        resourceStartedLastWeekDaos.add(dao);

        dao = new ResourceStartedLastWeekDao();
        dao.userOwner = "jane.doe";
        dao.accountId = "Nova";
        dao.resourceId = "e-987654";
        dao.startDate = "2017-09-03 09:00:00";
        dao.cost = 70;
        resourceStartedLastWeekDaos.add(dao);

        return resourceStartedLastWeekDaos;
    }
}
