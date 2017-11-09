package loke.service;

import loke.config.AccountReader;
import loke.db.athena.AthenaClient;
import loke.service.resourcestartedlastweek.ResourceStartedLastWeek;
import loke.utils.ResourceLoader;
import loke.utils.ResourceLoaderTestUtility;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static loke.db.athena.JdbcManager.QueryResult;
import static loke.service.resourcestartedlastweek.ResourceStartedLastWeek.ResourceStartedLastWeekDao;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class ResourceStartedLastWeekTest {
    private static final String SQL_QUERY = ResourceLoader.getResource("sql/ResourceStartedLastWeek.sql");
    private AthenaClient athenaClient;
    private ResourceStartedLastWeek resourceStartedLastWeek;

    @Before
    public void setUp() throws Exception {
        athenaClient = mock(AthenaClient.class);
        String userOwnerRegExp = "john.doe";
        Map<String, String> accounts = new AccountReader().readCSV("accounts.csv");
        resourceStartedLastWeek = new ResourceStartedLastWeek(athenaClient, userOwnerRegExp, accounts);
    }

    @Test
    public void canCreateTable() throws Exception {
        List<ResourceStartedLastWeekDao> resultList = new ArrayList<>();
        resultList.add(createDbResponse("QA", "john.doe", "Ec2", "i-01def0a998e06c30e", "2017-09-19", 1000));
        resultList.add(createDbResponse("Nova", "john.doe", "Ec2", "v-01def02344e06c30e", "2017-09-20", 1000));

        QueryResult queryResult = new QueryResult();
        queryResult.setResultList(resultList);

        Mockito.when(athenaClient.executeQuery(SQL_QUERY, ResourceStartedLastWeekDao.class)).thenReturn(queryResult);

        String expected = ResourceLoaderTestUtility.loadResource("htmltables/ResourceStartedLastWeekTestTable.html");
        String result = resourceStartedLastWeek.getReports().get(0).getHtmlTable();
        assertEquals(expected, result);
    }



    public ResourceStartedLastWeekDao createDbResponse(String account, String userOwner, String productName, String resourceId, String startDate, double cost) {
        ResourceStartedLastWeekDao spendPerUser = new ResourceStartedLastWeekDao();
        spendPerUser.accountId = account;
        spendPerUser.userOwner = userOwner;
        spendPerUser.productName = productName;
        spendPerUser.resourceId = resourceId;
        spendPerUser.startDate = startDate;
        spendPerUser.cost = cost;
        return spendPerUser;
    }

}