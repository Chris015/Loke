package loke.service;

import com.googlecode.charts4j.*;
import loke.db.athena.AthenaClient;
import loke.db.athena.JdbcManager;
import loke.model.Report;
import loke.utils.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;

import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SpendPerEmployeeByResource implements Service {
    private static final Logger log = LogManager.getLogger(SpendPerEmployeeByResource.class);
    private static final String SQL_QUERY =
            ResourceLoader.getResource("sql/SpendPerEmployeeByResource.sql");
    private List<Calendar> daysBack = CalendarGenerator.getDaysBack(30);
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private AthenaClient athenaClient;
    private String userOwnerRegExp;
    private double generateReportThreshold;

    public SpendPerEmployeeByResource(AthenaClient athenaClient, String userOwnerRegExp,
                                      double generateReportThreshold) {
        this.athenaClient = athenaClient;
        this.userOwnerRegExp = userOwnerRegExp;
        this.generateReportThreshold = generateReportThreshold;
    }

    @Override
    public List<Report> getReports() {
        Map<String, User> users = sendRequest();
        return generateReports(users);
    }

    private List<Report> generateReports(Map<String, User> users) {
        log.info("Generating reports for spend per user listed by resource the last {} days", daysBack.size());
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
        ScaleChecker.Scale scale = checkScale(user);
        List<String> xAxisLabels = getXAxisLabels();
        List<Line> lineChartPlots = createPlots(user, scale);
        LineChart chart = GCharts.newLineChart(lineChartPlots);
        configureChart(xAxisLabels, chart, user, scale);
        return chart.toURLString();
    }

    private String generateHTMLTable(User user) {
        VelocityEngine velocityEngine = new VelocityEngine();
        Properties p = new Properties();
        p.setProperty("resource.loader", "class");
        p.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        velocityEngine.init(p);

        VelocityContext context = new VelocityContext();
        context.put("dates", daysBack);
        context.put("user", user);
        context.put("colspan", daysBack.size() + 2);
        context.put("simpleDateForamt", new SimpleDateFormat("MMM dd, YYYY", Locale.US));
        context.put("dateFormat", this.dateFormat);
        context.put("decimalFormatter", DecimalFormatter.class);

        Template template = velocityEngine.getTemplate("templates/spendperemployeebyresource.vm");

        StringWriter stringWriter = new StringWriter();
        template.merge(context, stringWriter);

        return stringWriter.toString();
    }

    private ScaleChecker.Scale checkScale(User user) {
        List<Double> dailyCosts = new ArrayList<>();

        for (Calendar calendar : daysBack) {
            for (Resource resource : user.getResources().values()) {
                Day day = resource.getDays().get(dateFormat.format(calendar.getTime()));
                if (day != null) {
                    dailyCosts.add(day.getDailyCost());
                } else {
                    dailyCosts.add(0.0);
                }
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

    private void configureChart(List<String> daysXAxisLabels, LineChart chart, User user, ScaleChecker.Scale scale) {
        int chartWidth = 1000;
        int chartHeight = 300;
        chart.addYAxisLabels(AxisLabelsFactory.newNumericAxisLabels(scale.getyAxisLabels()));
        chart.addXAxisLabels(AxisLabelsFactory.newAxisLabels(daysXAxisLabels));
        chart.addYAxisLabels(AxisLabelsFactory.newAxisLabels("Cost in " + scale.getSuffix(), 50));

        chart.addXAxisLabels(AxisLabelsFactory.newAxisLabels("Day", 50));
        chart.setSize(chartWidth, chartHeight);
        chart.setTitle("Total spend for "
                + user.getUserName()
                + " the past "
                + daysBack.size()
                + " days "
                + DecimalFormatter.format(user.calculateTotalCost(), 2)
                + " USD");
    }

    private List<Line> createPlots(User user, ScaleChecker.Scale scale) {
        List<Line> plots = new ArrayList<>();
        for (Resource resource : user.getResources().values()) {
            List<Double> lineSizeValues = getLineSize(resource, scale);
            double total = getResourceTotal(resource);
            Line lineChartPlot = Plots.newLine(Data.newData(lineSizeValues), ColorPicker.getNextColor(),
                    resource.getResourceName() + " " + DecimalFormatter.format(total, 2));
            plots.add(0, lineChartPlot);
        }
        return plots;
    }

    private double getResourceTotal(Resource resource) {
        double total = 0.0;
        for (Double cost : getDailyCosts(resource)) {
            total += cost;
        }
        return total;
    }

    private List<Double> getLineSize(Resource resource, ScaleChecker.Scale scale) {
        List<Double> lineSizeValues = new ArrayList<>();
        for (Double cost : getDailyCosts(resource)) {
            lineSizeValues.add(cost / scale.getDivideBy());
        }
        return lineSizeValues;
    }

    private List<Double> getDailyCosts(Resource resource) {
        List<Double> data = new ArrayList<>();
        for (Calendar calendar : daysBack) {
            Day day = resource.getDays().get(dateFormat.format(calendar.getTime()));
            if (day == null) {
                data.add(0.0);
            } else {
                data.add(resource.getDays().get(dateFormat.format(calendar.getTime())).getDailyCost());
            }
        }
        return data;
    }

    private Map<String, User> sendRequest() {
        log.trace("Fetching data and mapping objects");
        Map<String, User> users = new HashMap<>();
        JdbcManager.QueryResult<SpendPerEmployeeByResourceDao> queryResult =
                athenaClient.executeQuery(SQL_QUERY, SpendPerEmployeeByResourceDao.class);
        for (SpendPerEmployeeByResourceDao dao : queryResult.getResultList()) {
            if (!dao.userOwner.matches(userOwnerRegExp)) {
                continue;
            }

            String userName = dao.userOwner;
            String startDate = dao.startDate;
            String productName = dao.productName;

            if (!users.containsKey(userName)) {
                users.put(userName, new User(userName));
            }
            if (!users.get(userName).getResources().containsKey(productName)) {
                users.get(userName).addResource(new Resource(productName));
            }

            Calendar date = Calendar.getInstance();
            try {
                date.setTime(dateFormat.parse(startDate));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            Day day = new Day(date, dao.cost);
            users.get(userName).getResources().get(productName).addDay(dateFormat.format(day.getDate().getTime()), day);
        }
        log.trace("Done mapping objects");
        return users;
    }

    public static class SpendPerEmployeeByResourceDao {
        @JdbcManager.Column(value = "user_owner")
        public String userOwner;
        @JdbcManager.Column(value = "product_name")
        public String productName;
        @JdbcManager.Column(value = "cost")
        public double cost;
        @JdbcManager.Column(value = "start_date")
        public String startDate;
    }

    public class User {
        private String userName;
        private HashMap<String, Resource> resources;

        public User(String userName) {
            this.userName = userName;
            this.resources = new HashMap<>();
        }

        public void addResource(Resource resource) {
            this.resources.put(resource.getResourceName(), resource);
        }

        public String getUserName() {
            return userName;
        }

        public HashMap<String, Resource> getResources() {
            return resources;
        }

        public double calculateTotalCost() {
            double totalCost = 0;
            for (Resource resource : resources.values()) {
                for (Day day : resource.getDays().values()) {
                    totalCost += day.getDailyCost();
                }
            }
            return totalCost;
        }
    }

    public class Resource {
        private String resourceName;
        private HashMap<String, Day> days;

        public Resource(String resourceName) {
            this.resourceName = resourceName;
            this.days = new HashMap<>();
        }

        public Day getDay(String date) {
            return days.get(date);
        }

        public void addDay(String key, Day day) {
            this.days.put(key, day);
        }

        public String getResourceName() {
            return resourceName;
        }

        public HashMap<String, Day> getDays() {
            return days;
        }

        public double getResourceTotal() {
            double total = 0;
            for (Day day : days.values()) {
                if (day != null) {
                    total += day.getDailyCost();
                }
            }
            return total;
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
