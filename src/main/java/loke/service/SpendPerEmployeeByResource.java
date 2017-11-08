package loke.service;

import com.googlecode.charts4j.*;
import loke.db.athena.AthenaClient;
import loke.db.athena.JdbcManager;
import loke.model.Report;
import loke.utils.CalendarGenerator;
import loke.utils.ColorPicker;
import loke.utils.DecimalFormatter;
import loke.utils.ResourceLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SpendPerEmployeeByResource implements Service {
    private static final Logger log = LogManager.getLogger(SpendPerEmployeeByResource.class);
    private static final String SQL_QUERY = ResourceLoader.getResource("sql/SpendPerEmployeeByResource.sql");
    private static final List<Calendar> DAYS_BACK = CalendarGenerator.getDaysBack(60);
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private AthenaClient athenaClient;
    private String userOwnerRegExp;
    private VelocityEngine velocityEngine;

    public SpendPerEmployeeByResource(AthenaClient athenaClient, String userOwnerRegExp, VelocityEngine velocityEngine) {
        this.athenaClient = athenaClient;
        this.userOwnerRegExp = userOwnerRegExp;
        this.velocityEngine = velocityEngine;
    }

    @Override
    public List<Report> getReports() {
        Map<String, User> users = sendRequest();
        return generateReports(users);
    }

    private List<Report> generateReports(Map<String, User> users) {
        List<Report> reports = new ArrayList<>();
        for (User user : users.values()) {
            Report report = new Report(user.getUserName());
            report.setChartUrl(generateChartUrl(user));
            report.setHtmlTable(generateHTMLTable(user));
            reports.add(report);
            log.info("Report generated for: {}", user.getUserName());
        }
        return reports;
    }

    private String generateChartUrl(User user) {
        ColorPicker.resetColor();
        Scale scale = checkScale(user);
        List<String> xAxisLabels = getXAxisLabels();
        List<Line> lineChartPlots = createPlots(user, scale);
        LineChart chart = GCharts.newLineChart(lineChartPlots);
        configureChart(xAxisLabels, chart, user, scale);
        return chart.toURLString();
    }

    private String generateHTMLTable(User user) {
        VelocityContext context = new VelocityContext();
        context.put("dates", DAYS_BACK);
        context.put("user", user);
        context.put("colspan", DAYS_BACK.size() + 2);
        context.put("simpleDateForamt", new SimpleDateFormat("MMM dd, YYYY", Locale.US));
        context.put("dateFormat", this.dateFormat);
        context.put("decimalFormatter", DecimalFormatter.class);

        Template template = velocityEngine.getTemplate("src/templates/spendperemployeebyresource.vm");

        StringWriter stringWriter = new StringWriter();
        template.merge(context, stringWriter);

        return stringWriter.toString();
    }

    private Scale checkScale(User user) {
        List<Double> dailyCosts = new ArrayList<>();

        for (Calendar calendar : DAYS_BACK) {
            double dailyCost = 0.0;
            for (Resource resource : user.getResources().values()) {

                Day day = resource.getDays().get(dateFormat.format(calendar.getTime()));
                dailyCost += (day == null) ? 0.0 : day.getDailyCost();
            }
            dailyCosts.add(dailyCost);
        }

        dailyCosts.sort((o1, o2) -> (int) (o1 + o2));

        if (dailyCosts.get(0) > 100) return Scale.OVER_HUNDRED;
        if (dailyCosts.get(0) < 10) return Scale.UNDER_TEN;
        return Scale.UNDER_HUNDRED;
    }

    private List<String> getXAxisLabels() {
        List<String> labels = new ArrayList<>();

        for (Calendar day : DAYS_BACK) {
            String date = dateFormat.format(day.getTime());
            if (!labels.contains(date)) {
                labels.add(date.substring(8, 10));
            }
        }
        return labels;
    }

    private void configureChart(List<String> daysXAxisLabels, LineChart chart, User user, Scale scale) {
        int chartWidth = 1000;
        int chartHeight = 300;
        chart.addYAxisLabels(AxisLabelsFactory.newNumericAxisLabels(scale.getyAxisLabels()));
        chart.addXAxisLabels(AxisLabelsFactory.newAxisLabels(daysXAxisLabels));
        chart.addYAxisLabels(AxisLabelsFactory.newAxisLabels("Cost in " + scale.getSuffix(), 50));

        chart.addXAxisLabels(AxisLabelsFactory.newAxisLabels("Day", 50));
        chart.setSize(chartWidth, chartHeight);
        chart.setTitle("Total spend for " + user.getUserName() + " the past " + DAYS_BACK.size() + " days " + DecimalFormatter.format(user.calculateTotalCost(), 2) + " USD");
    }

    private List<Line> createPlots(User user, Scale scale) {
        List<Line> plots = new ArrayList<>();
        for (Resource resource : user.getResources().values()) {
            List<Double> lineSizeValues = getLineSize(resource, scale);
            double total = getResourceTotal(resource);
            Line lineChartPlot = Plots.newLine(Data.newData(lineSizeValues), ColorPicker.getNextColor(), resource.getResourceName() + " " + DecimalFormatter.format(total, 2));
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

    private List<Double> getLineSize(Resource resource, Scale scale) {
        List<Double> lineSizeValues = new ArrayList<>();
        for (Double cost : getDailyCosts(resource)) {
            lineSizeValues.add(cost / scale.getDivideBy());
        }
        return lineSizeValues;
    }

    private List<Double> getDailyCosts(Resource resource) {
        List<Double> data = new ArrayList<>();
        for (Calendar calendar : DAYS_BACK) {
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
        log.info("Fetching data and mapping objects");
        Map<String, User> users = new HashMap<>();
        JdbcManager.QueryResult<SpendPerEmployeeByResourceDao> queryResult = athenaClient.executeQuery(SQL_QUERY, SpendPerEmployeeByResourceDao.class);
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
        log.info("Done mapping objects");
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
