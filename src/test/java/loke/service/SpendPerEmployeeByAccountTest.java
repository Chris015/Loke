package loke.service;

import loke.aws.db.AthenaClient;
import loke.utils.CalendarGenerator;
import loke.utils.ResourceLoader;
import loke.utils.SqlConfigInjector;
import org.junit.Before;
import org.junit.Test;
import testutilities.ResourceLoaderTestUtility;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static loke.aws.db.JdbcManager.QueryResult;
import static loke.service.SpendPerEmployeeByAccount.SpendPerEmployeeAndAccountDao;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpendPerEmployeeByAccountTest {
    private String sqlQuery;
    private AthenaClient athenaClient;
    private SpendPerEmployeeByAccount spendPerEmployeeByAccount;
    private Clock clock;

    @Before
    public void setUp() throws Exception {
        clock = mock(Clock.class);
        CalendarGenerator.clock = clock;
        when(clock.instant()).thenReturn(Instant.parse("2017-09-30T00:00:00Z"));
        athenaClient = mock(AthenaClient.class);
        String userOwnerRegExp = "john.doe";
        SqlConfigInjector sqlConfigInjector = new SqlConfigInjector("database", "table");
        this.sqlQuery = sqlConfigInjector.injectSqlConfig(ResourceLoader.getResource("sql/SpendPerEmployeeByAccount.sql"));
        spendPerEmployeeByAccount = new SpendPerEmployeeByAccount(athenaClient, userOwnerRegExp, 0, new HashMap<>(), sqlConfigInjector);
    }

    @Test
    public void canCreateTable() throws Exception {
        List<SpendPerEmployeeByAccount.SpendPerEmployeeAndAccountDao> spendPerEmployeeAndAccountDaos = new ArrayList<>();
        spendPerEmployeeAndAccountDaos.add(createDbResponse("john.doe", "QA", "Ec2", "2017-09-01 09:00:00", 100));
        spendPerEmployeeAndAccountDaos.add(createDbResponse("john.doe", "QA", "Ec2", "2017-09-02 09:00:00", 100));
        spendPerEmployeeAndAccountDaos.add(createDbResponse("john.doe", "QA", "Ec2", "2017-09-03 09:00:00", 50));
        spendPerEmployeeAndAccountDaos.add(createDbResponse("john.doe", "QA", "S3", "2017-09-01 09:00:00", 300));
        spendPerEmployeeAndAccountDaos.add(createDbResponse("john.doe", "Nova", "S3", "2017-09-11 10:00:00", 100));
        QueryResult queryResult = new QueryResult();
        queryResult.setResultList(spendPerEmployeeAndAccountDaos);

        when(athenaClient.executeQuery(sqlQuery, SpendPerEmployeeAndAccountDao.class)).thenReturn(queryResult);

        String expected = ResourceLoaderTestUtility.loadResource("htmltables/SpendPerUserAndAccountTestTable.html");
        String result = spendPerEmployeeByAccount.getReports().get(0).getHtmlTable();
        assertEquals(expected, result);
    }

    private SpendPerEmployeeAndAccountDao createDbResponse(String userOwner, String accountId, String productName,
                                                           String startDate, double cost) {
        SpendPerEmployeeAndAccountDao spendPerEmployeeAndAccountDao = new SpendPerEmployeeAndAccountDao();
        spendPerEmployeeAndAccountDao.userOwner = userOwner;
        spendPerEmployeeAndAccountDao.accountId = accountId;
        spendPerEmployeeAndAccountDao.productName = productName;
        spendPerEmployeeAndAccountDao.startDate = startDate;
        spendPerEmployeeAndAccountDao.cost = cost;
        return spendPerEmployeeAndAccountDao;
    }

}