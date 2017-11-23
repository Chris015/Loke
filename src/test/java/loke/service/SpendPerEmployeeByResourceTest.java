package loke.service;

import loke.aws.db.AthenaClient;
import loke.aws.db.JdbcManager.QueryResult;
import loke.service.SpendPerEmployeeByResource.SpendPerEmployeeByResourceDao;
import loke.utils.CalendarGenerator;
import loke.utils.ResourceLoader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import testutilities.ResourceLoaderTestUtility;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpendPerEmployeeByResourceTest {

    private static final String SQL_QUERY = ResourceLoader.getResource("sql/SpendPerEmployeeByResource.sql");
    private AthenaClient athenaClient;
    private SpendPerEmployeeByResource spendPerEmployeeByResource;
    private Clock clock;

    @Before
    public void setUp() throws Exception {
        this.clock = mock(Clock.class);
        CalendarGenerator.clock = this.clock;
        when(clock.instant()).thenReturn(Instant.parse("2017-11-08T00:00:00Z"));
        athenaClient = mock(AthenaClient.class);
        String userOwnerRegExp = "john.doe";
        spendPerEmployeeByResource = new SpendPerEmployeeByResource(athenaClient, userOwnerRegExp, 0, null);
    }

    @Test
    public void canCreateTable() throws Exception {
        List<SpendPerEmployeeByResourceDao> resultList = new ArrayList<>();
        resultList.add(createDbResponse("john.doe", "S3", "2017-11-07", 200));
        resultList.add(createDbResponse("john.doe", "EC2", "2017-11-07", 100));
        resultList.add(createDbResponse("john.doe", "Elastic Map Reduce", "2017-11-05", 20000));
        resultList.add(createDbResponse("john.doe", "Loadbalancers", "2017-11-05", 1000));

        QueryResult queryResult = new QueryResult();
        queryResult.setResultList(resultList);

        Mockito.when(athenaClient.executeQuery(SQL_QUERY, SpendPerEmployeeByResourceDao.class)).thenReturn(queryResult);

        String expected = ResourceLoaderTestUtility.loadResource("htmltables/SpendPerEmployeeByResourceTestTable.html");
        String result = spendPerEmployeeByResource.getReports().get(0).getHtmlTable();
        assertEquals(expected, result);
    }

    public SpendPerEmployeeByResourceDao createDbResponse(String userOwner, String productName, String startDate, double cost) {
        SpendPerEmployeeByResourceDao spendPerUser = new SpendPerEmployeeByResourceDao();
        spendPerUser.userOwner = userOwner;
        spendPerUser.productName = productName;
        spendPerUser.startDate = startDate;
        spendPerUser.cost = cost;
        return spendPerUser;
    }
}