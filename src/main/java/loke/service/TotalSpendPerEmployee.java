package loke.service;

import com.googlecode.charts4j.*;
import loke.aws.db.AthenaClient;
import loke.aws.db.JdbcManager;
import loke.model.Report;
import loke.model.TotalReport;
import loke.utils.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class TotalSpendPerEmployee implements Service {
    private static final Logger log = LogManager.getLogger(TotalSpendPerEmployee.class);
    private AthenaClient athenaClient;
    private String sqlQuery;
    private List<Calendar> daysBack = CalendarGenerator.getDaysBack(30);
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private ColorPicker colorPicker;
    private String userOwnerRegExp;
    private double generateReportThreshold;

    public TotalSpendPerEmployee(AthenaClient athenaClient, String userOwnerRegExp, double generateReportThreshold,
                                 ColorPicker colorPicker, SqlConfigInjector configInjector) {
        this.athenaClient = athenaClient;
        this.userOwnerRegExp = userOwnerRegExp;
        this.generateReportThreshold = generateReportThreshold;
        this.colorPicker = colorPicker;
        this.sqlQuery = configInjector.injectSqlConfig(ResourceLoader.getResource("sql/TotalSpendPerEmployee.sql"));
    }


    @Override
    public List<Report> getReports() {
        Map<String, User> users = sendRequest();
        return generateReports(users);
    }

    private List<Report> generateReports(Map<String, User> users) {
        log.info("Generating reports for total spend per user the last {} days", daysBack.size());
        List<Report> reports = new ArrayList<>();
        for (User user : users.values()) {
            if (user.calculateTotalCost() < generateReportThreshold) {
                log.info("User: {} fell beneith the account threshold of: {}. Account total: {}",
                        user.getUserName(), generateReportThreshold, user.calculateTotalCost());
                continue;
            }
            colorPicker.resetColor();
            ScaleChecker.Scale scale = checkScale(user);
            List<String> xAxisLabels = getXAxisLabels();
            List<Line> lineChartPlots = createPlots(user, scale);
            LineChart chart = GCharts.newLineChart(lineChartPlots);
            configureChart(xAxisLabels, chart, user, scale);
            Report report = new TotalReport(user.getUserName());
            report.setChartUrl(chart.toURLString());
            reports.add(report);
            log.info("Report generated for: {}", user.getUserName());
        }
        log.info("Reports generated: {}", reports.size());
        return reports;
    }

    private ScaleChecker.Scale checkScale(User user) {
        List<Double> dailyCosts = new ArrayList<>();

        for (Calendar calendar : daysBack) {
            Day day = user.getDays().get(dateFormat.format(calendar.getTime()));
            dailyCosts.add((day == null) ? 0.0 : day.getDailyCost());
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
                + " the past " + daysBack.size()
                + " days "
                + DecimalFormatter.format(user.calculateTotalCost(), 2)
                + " USD");
    }

    private List<Line> createPlots(User user, ScaleChecker.Scale scale) {
        List<Line> plots = new ArrayList<>();
        List<Double> lineSizeValues = getLineSize(user, scale);
        Line lineChartPlot = Plots.newLine(Data.newData(lineSizeValues), colorPicker.getNextColor());
        plots.add(0, lineChartPlot);
        return plots;
    }

    private List<Double> getLineSize(User user, ScaleChecker.Scale scale) {
        List<Double> lineSizeValues = new ArrayList<>();
        for (Calendar calendar : daysBack) {
            Day day = user.getDays().get(dateFormat.format(calendar.getTime()));
            lineSizeValues.add((day != null) ? day.getDailyCost() / scale.getDivideBy() : 0);
        }
        return lineSizeValues;
    }

    private Map<String, User> sendRequest() {
        log.trace("Fetching data and mapping objects");
        Map<String, User> users = new HashMap<>();
        JdbcManager.QueryResult<TotalSpendPerEmployeeDao> queryResult =
                athenaClient.executeQuery(sqlQuery, TotalSpendPerEmployeeDao.class);
        for (TotalSpendPerEmployeeDao dao : queryResult.getResultList()) {
            if (!dao.userOwner.matches(userOwnerRegExp)) {
                continue;
            }

            String userName = dao.userOwner;
            String startDate = dao.startDate;

            if (!users.containsKey(userName)) {
                users.put(userName, new User(userName));
            }

            Calendar date = Calendar.getInstance();
            try {
                date.setTime(dateFormat.parse(startDate));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            Day day = new Day(date, dao.cost);
            users.get(userName).addDay(dateFormat.format(day.getDate().getTime()), day);
        }
        log.trace("Done mapping objects");
        return users;
    }

    public static class TotalSpendPerEmployeeDao {
        @JdbcManager.Column(value = "user_owner")
        public String userOwner;
        @JdbcManager.Column(value = "cost")
        public double cost;
        @JdbcManager.Column(value = "start_date")
        public String startDate;
    }


    private class User {
        private String userName;
        private Map<String, Day> days;

        public User(String userName) {
            this.userName = userName;
            this.days = new HashMap<>();
        }

        public String getUserName() {
            return userName;
        }

        public Map<String, Day> getDays() {
            return days;
        }

        public double calculateTotalCost() {
            double totalCost = 0;
            for (Day day : days.values()) {
                totalCost += day.getDailyCost();
            }
            return totalCost;
        }

        public void addDay(String key, Day day) {
            days.put(key, day);
        }
    }

    private class Day {
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
