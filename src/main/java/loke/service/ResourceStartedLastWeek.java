package loke.service;

import loke.db.athena.AthenaClient;
import loke.db.athena.JdbcManager;
import loke.model.Report;
import loke.utils.DecimalFormatter;
import loke.utils.ResourceLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;

import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class ResourceStartedLastWeek implements Service {
    private static final Logger log = LogManager.getLogger(ResourceStartedLastWeek.class);
    private static final String SQL_QUERY =
            ResourceLoader.getResource("sql/ResourceStartedLastWeek.sql");
    private AthenaClient jdbcClient;
    private String userOwnerRegExp;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private Map<String, String> csvAccounts;

    public ResourceStartedLastWeek(AthenaClient athenaClient, String userOwnerRegExp, Map<String, String> csvAccounts) {
        this.jdbcClient = athenaClient;
        this.userOwnerRegExp = userOwnerRegExp;
        this.csvAccounts = csvAccounts;
    }

    @Override
    public List<Report> getReports() {
        Map<String, User> users = sendRequest();
        return generateReports(users);
    }

    private List<Report> generateReports(Map<String, User> users) {
        long amountToSubtract = 7;
        LocalDate start = LocalDate.now().minus(amountToSubtract, ChronoUnit.DAYS);
        LocalDate end = LocalDate.now();
        log.info("Generating reports for resources started between {} and {}", start, end);

        List<Report> reports = new ArrayList<>();
        for (User user : users.values()) {
            Report report = new Report(user.getUserName());
            report.setHtmlTable(generateHTMLTable(user));
            reports.add(report);
            log.info("Report generated for: {}", user.getUserName());
        }
        log.info("Reports generated: {}", reports.size());
        return reports;
    }

    private String generateHTMLTable(User user) {
        VelocityEngine velocityEngine = new VelocityEngine();
        Properties p = new Properties();
        p.setProperty("resource.loader", "class");
        p.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        velocityEngine.init(p);

        VelocityContext context = new VelocityContext();
        context.put("user", user);
        context.put("decimalFormatter", DecimalFormatter.class);
        context.put("dateFormat", new SimpleDateFormat("MMM dd, YYYY", Locale.US));

        Template template = velocityEngine.getTemplate("templates/resourcesstartedlastweek.vm");

        StringWriter stringWriter = new StringWriter();
        template.merge(context, stringWriter);

        return stringWriter.toString().trim();
    }

    private Map<String, User> sendRequest() {
        log.trace("Fetching data and mapping objects");
        Map<String, User> users = new HashMap<>();
        JdbcManager.QueryResult<ResourceStartedLastWeekDao> queryResult =
                jdbcClient.executeQuery(SQL_QUERY, ResourceStartedLastWeekDao.class);
        for (ResourceStartedLastWeekDao dao : queryResult.getResultList()) {
            if (!dao.userOwner.matches(userOwnerRegExp)) {
                continue;
            }

            if (!users.containsKey(dao.userOwner)) {
                users.put(dao.userOwner, new User(dao.userOwner));
            }

            Calendar calendar = Calendar.getInstance();
            try {
                Date date = dateFormat.parse(dao.startDate);
                calendar.setTime(date);
            } catch (ParseException e) {
                e.printStackTrace();
            }

            String accountId = dao.accountId;
            String accountName = csvAccounts.get(accountId);
            accountId = (accountName != null) ? accountName : accountId;

            users.get(dao.userOwner).addResource(
                    new Resource(accountId, dao.productName, dao.resourceId, calendar, dao.cost
                    ));
        }
        log.trace("Done mapping objects");
        return users;
    }

    public static class ResourceStartedLastWeekDao {
        @JdbcManager.Column(value = "account_id")
        public String accountId;
        @JdbcManager.Column(value = "user_owner")
        public String userOwner;
        @JdbcManager.Column(value = "product_name")
        public String productName;
        @JdbcManager.Column(value = "resource_id")
        public String resourceId;
        @JdbcManager.Column(value = "start_date")
        public String startDate;
        @JdbcManager.Column(value = "cost")
        public double cost;
    }

    public class User {
        private String userName;
        private List<Resource> resources;

        public User(String userName) {
            this.userName = userName;
            this.resources = new ArrayList<>();
        }

        public double calculateTotalSpend() {
            double total = 0;
            for (Resource resource : resources) {
                total += resource.getCost();
            }
            return total;
        }

        public String getUserName() {
            return userName;
        }

        public List<Resource> getResources() {
            return resources;
        }

        public void addResource(Resource resource) {
            resources.add(resource);
        }
    }

    public class Resource {
        private String accountId;
        private String productName;
        private String resourceId;
        private Calendar startDate;
        private double cost;

        public Resource(String accountId, String productName, String resourceId, Calendar startDate, double cost) {
            this.accountId = accountId;
            this.productName = productName;
            this.resourceId = resourceId;
            this.startDate = startDate;
            this.cost = cost;
        }

        public String getAccountId() {
            return accountId;
        }

        public String getProductName() {
            return productName;
        }

        public String getResourceId() {
            return resourceId;
        }

        public Calendar getStartDate() {
            return startDate;
        }

        public double getCost() {
            return cost;
        }
    }
}
