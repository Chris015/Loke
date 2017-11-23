package loke.service;

import com.googlecode.charts4j.*;
import loke.aws.db.AthenaClient;
import loke.aws.db.JdbcManager;
import loke.model.Report;
import loke.utils.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SpendPerEmployeeByAccount implements Service {
    private static final Logger log = LogManager.getLogger(SpendPerEmployeeByAccount.class);
    private String sqlQuery;
    private List<Calendar> daysBack = CalendarGenerator.getDaysBack(30);
    private AthenaClient jdbcClient;
    private String userOwnerRegExp;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private double generateReportThreshold;
    private Map<String, String> csvAccounts;

    public SpendPerEmployeeByAccount(AthenaClient athenaClient, String userOwnerRegExp,
                                     double generateReportThreshold, Map<String, String> csvAccounts, SqlConfigInjector configInjector) {
        this.jdbcClient = athenaClient;
        this.userOwnerRegExp = userOwnerRegExp;
        this.generateReportThreshold = generateReportThreshold;
        this.csvAccounts = csvAccounts;
        this.sqlQuery = configInjector.injectSqlConfig(ResourceLoader.getResource("sql/SpendPerEmployeeByAccount.sql"));
    }

    @Override
    public List<Report> getReports() {
        Map<String, User> users = sendRequest();
        return generateReports(users);
    }

    private List<Report> generateReports(Map<String, User> users) {
        log.info("Generating reports for spend per user listed by account the last {} days", daysBack.size());
        List<Report> reports = new ArrayList<>();
        for (User user : users.values()) {
            if (user.calculateTotalCost() < generateReportThreshold) {
                log.info("User: {} fell beneith the account threshold of: {}. Account total: {}", user.getUserName(),
                        generateReportThreshold, user.calculateTotalCost());
                continue;
            }
            Report report = new Report(user.getUserName());
            report.setChartUrl(generateChartUrl(user));
            report.setHtmlTable(generateHTMLTable(user));
            reports.add(report);
            log.info("Report generated for: {}", user.getUserName());
        }
        log.info("Reports generated: {}", reports.size());
        return reports;
    }

    private String generateChartUrl(User user) {
        ColorPicker.resetColor();
        ScaleChecker.Scale scale = checkScale(user.getAccounts().values());
        List<String> xAxisLabels = getXAxisLabels();
        List<Line> lineChartPlots = createPlots(user, scale);
        LineChart chart = GCharts.newLineChart(lineChartPlots);
        configureChart(xAxisLabels, chart, user, scale, user.getUserName());
        return chart.toURLString();
    }

    private String generateHTMLTable(User user) {
        VelocityEngine velocityEngine = new VelocityEngine();

        Properties p = new Properties();
        p.setProperty("resource.loader", "class");
        p.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        velocityEngine.init(p);

        VelocityContext context = new VelocityContext();
        context.put("userName", user.getUserName());
        context.put("generateReportThreshold", this.generateReportThreshold);
        context.put("dates", daysBack);
        context.put("accounts", user.getAccounts().values());
        context.put("total", user.calculateTotalCost());
        context.put("colspan", daysBack.size() + 2);
        context.put("simpleDateForamt", new SimpleDateFormat("MMM dd, YYYY", Locale.US));
        context.put("dateFormat", this.dateFormat);
        context.put("decimalFormatter", DecimalFormatter.class);

        Template template = velocityEngine.getTemplate("templates/spendperemployeebyaccount.vm");

        StringWriter stringWriter = new StringWriter();
        template.merge(context, stringWriter);

        return stringWriter.toString().trim();
    }

    private void configureChart(List<String> daysXAxisLabels, LineChart chart, User user, ScaleChecker.Scale scale,
                                String userName) {
        int chartWidth = 1000;
        int chartHeight = 300;
        chart.addYAxisLabels(AxisLabelsFactory.newNumericAxisLabels(scale.getyAxisLabels()));
        chart.addXAxisLabels(AxisLabelsFactory.newAxisLabels(daysXAxisLabels));
        chart.addYAxisLabels(AxisLabelsFactory.newAxisLabels("Cost in " + scale.getSuffix(), 50));
        chart.addXAxisLabels(AxisLabelsFactory.newAxisLabels("Day", 50));
        chart.setSize(chartWidth, chartHeight);
        chart.setTitle("Total spend for "
                + userName
                + " by account the past "
                + daysBack.size()
                + " days. "
                + DecimalFormatter.format(user.calculateTotalCost(), 2) + " UDS.");
    }

    private List<Line> createPlots(User user, ScaleChecker.Scale scale) {
        List<Line> plots = new ArrayList<>();

        for (Account account : user.getAccounts().values()) {
            List<Double> lineSizeValues = new ArrayList<>();
            for (Calendar calendar : daysBack) {
                lineSizeValues.add(
                        account.getAccountDailyTotal(dateFormat.format(calendar.getTime())) / scale.getDivideBy()
                );
            }
            Line lineChartPlot = Plots.newLine(
                    Data.newData(lineSizeValues),
                    ColorPicker.getNextColor(),
                    account.getAccountId()
                            + " "
                            + DecimalFormatter.format(account.getAccountTotal(), 2));
            plots.add(0, lineChartPlot);
        }
        return plots;
    }

    private ScaleChecker.Scale checkScale(Collection<Account> accounts) {
        List<Double> dailyCosts = new ArrayList<>();
        for (Account account : accounts) {
            for (Calendar calendar : daysBack) {
                dailyCosts.add(account.getAccountDailyTotal(dateFormat.format(calendar.getTime())));
            }
        }
        dailyCosts.sort((o1, o2) -> Double.compare(o2, o1));
        return ScaleChecker.checkScale(dailyCosts.get(0));
    }

    private List<String> getXAxisLabels() {
        List<String> labels = new ArrayList<>();

        for (Calendar day : daysBack) {
            String date = dateFormat.format(day.getTime());
            if (!labels.contains(date)) {
                labels.add(date.substring(8, 10));
            }
        }
        return labels;
    }

    private Map<String, User> sendRequest() {
        log.trace("Fetching data and mapping objects");
        Map<String, User> users = new HashMap<>();
        JdbcManager.QueryResult<SpendPerEmployeeAndAccountDao> queryResult =
                jdbcClient.executeQuery(sqlQuery, SpendPerEmployeeAndAccountDao.class);
        for (SpendPerEmployeeAndAccountDao dao : queryResult.getResultList()) {
            if (!dao.userOwner.matches(userOwnerRegExp)) {
                continue;
            }

            if (!users.containsKey(dao.userOwner)) {
                users.put(dao.userOwner, new User(dao.userOwner));
            }

            User user = users.get(dao.userOwner);
            if (!user.getAccounts().containsKey(dao.accountId)) {
                String accountName = csvAccounts.get(dao.accountId);
                String tmpAccountId = (accountName != null) ? accountName : dao.accountId;
                user.addAccount(dao.accountId, new Account(tmpAccountId));
            }

            Account account = user.getAccounts().get(dao.accountId);
            if (!account.getResources().containsKey(dao.productName)) {
                account.addResource(dao.productName, new Resource(dao.productName));
            }

            Resource resource = account.getResources().get(dao.productName);
            Calendar date = Calendar.getInstance();
            try {
                date.setTime(dateFormat.parse(dao.startDate));
            } catch (ParseException e) {
                e.printStackTrace();
            }

            Day day = new Day(date, dao.cost);
            resource.getDays().put(dateFormat.format(day.getDate().getTime()), day);
        }
        log.trace("Done mapping objects");
        return users;
    }

    public static class SpendPerEmployeeAndAccountDao {
        @JdbcManager.Column(value = "user_owner")
        public String userOwner;
        @JdbcManager.Column(value = "account_id")
        public String accountId;
        @JdbcManager.Column(value = "product_name")
        public String productName;
        @JdbcManager.Column(value = "start_date")
        public String startDate;
        @JdbcManager.Column(value = "cost")
        public double cost;
    }

    public class User {
        private String userName;
        private Map<String, Account> accounts;

        public User(String userName) {
            this.userName = userName;
            this.accounts = new HashMap<>();
        }

        public double calculateTotalCost() {
            double total = 0;
            for (Account account : accounts.values()) {
                total += account.getAccountTotal();
            }
            return total;
        }

        public String getUserName() {
            return userName;
        }

        public Map<String, Account> getAccounts() {
            return accounts;
        }

        public void addAccount(String key, Account account) {
            this.accounts.put(key, account);
        }
    }

    public class Account {
        private String accountId;
        private Map<String, Resource> resources;

        public Account(String accountId) {
            this.accountId = accountId;
            this.resources = new HashMap<>();
        }

        public String getAccountId() {
            return accountId;
        }

        public double getAccountDailyTotal(String date) {
            double total = 0.00;
            for (Resource resource : resources.values()) {
                Day day = resource.getDay(date);
                if (day != null) {
                    total += resource.getDay(date).getDailyCost();
                }
            }
            return total;
        }

        public double getAccountTotal() {
            double total = 0;
            for (Resource resource : resources.values()) {
                total += resource.getResourceTotal();
            }
            return total;
        }

        public Map<String, Resource> getResources() {
            return resources;
        }

        public void addResource(String key, Resource resource) {
            this.resources.put(key, resource);
        }
    }

    public class Resource {
        private String productName;
        private Map<String, Day> days;

        public Resource(String productName) {
            this.productName = productName;
            this.days = new HashMap<>();
        }

        public String getResourceName() {
            return productName;
        }

        public double getResourceTotal() {
            double total = 0;
            for (Day day : days.values()) {
                total += day.getDailyCost();
            }
            return total;
        }

        public Map<String, Day> getDays() {
            return days;
        }

        public Day getDay(String key) {
            return days.get(key);
        }
    }

    public class Day {
        private Calendar date;
        private double dailyCost;

        public Day(Calendar date, double dailyCost) {
            this.date = date;
            this.dailyCost = dailyCost;
        }

        public Calendar getDate() {
            return date;
        }

        public double getDailyCost() {
            return dailyCost;
        }
    }

}
