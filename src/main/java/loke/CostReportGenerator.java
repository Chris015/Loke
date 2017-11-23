package loke;

import loke.aws.db.AthenaClient;
import loke.model.Employee;
import loke.model.Report;
import loke.service.*;
import loke.utils.SqlConfigInjector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CostReportGenerator {
    private Logger log = LogManager.getLogger(CostReportGenerator.class);
    private List<Service> employeeServices;
    private List<Service> adminServices;
    private SqlConfigInjector configInjector;

    public CostReportGenerator(AthenaClient athenaClient, String userOwnerRegExp, double generateReportThreshold,
                               Map<String, String> csvAccounts, SqlConfigInjector configInjector) {
        this.employeeServices = new ArrayList<>();
        this.adminServices = new ArrayList<>();
        this.configInjector = configInjector;

        TotalSpendPerEmployee totalSpendPerEmployee =
                new TotalSpendPerEmployee(athenaClient, userOwnerRegExp, generateReportThreshold, configInjector);
        SpendPerEmployeeByResource spendPerEmployeeByResource =
                new SpendPerEmployeeByResource(athenaClient, userOwnerRegExp, generateReportThreshold, configInjector);
        SpendPerEmployeeByAccount spendPerEmployeeByAccount =
                new SpendPerEmployeeByAccount(athenaClient, userOwnerRegExp, generateReportThreshold, csvAccounts, configInjector);
        ResourceStartedLastWeek resourceStartedLastWeek =
                new ResourceStartedLastWeek(athenaClient, userOwnerRegExp, csvAccounts, configInjector);

        this.employeeServices.add(spendPerEmployeeByResource);
        this.employeeServices.add(spendPerEmployeeByAccount);
        this.employeeServices.add(resourceStartedLastWeek);

        this.adminServices.add(totalSpendPerEmployee);
        this.adminServices.add(spendPerEmployeeByAccount);
    }

    public List<Employee> generateReports() {
        log.info("Generating employee-reports");
        List<Report> employeeReports = getReports(this.employeeServices);
        log.info("Total employee-reports generated: {}", employeeReports.size());
        return orderChartsByUser(employeeReports);
    }

    public List<Employee> generateAdminReports() {
        log.info("Generating admin-reports");
        List<Report> adminReports = getReports(this.adminServices);
        log.info("Admin-reports generated: {}", adminReports.size());
        return orderChartsByUser(adminReports);
    }

    private List<Report> getReports(List<Service> services) {
        List<Report> reports = new ArrayList<>();
        for (Service service : services) {
            reports.addAll(service.getReports());
        }
        return reports;
    }

    private List<Employee> orderChartsByUser(List<Report> reports) {
        List<Employee> employees = new ArrayList<>();
        for (Report report : reports) {
            if (employees.stream().noneMatch(user -> user.getUserName().equals(report.getOwner()))) {
                employees.add(new Employee(report.getOwner()));
            }
            Employee employee = findUser(employees, report.getOwner());
            if (employee != null) {
                employee.getReports().add(report);
            }
        }
        return employees;
    }

    private Employee findUser(List<Employee> employees, String owner) {
        for (Employee employee : employees) {
            if (employee.getUserName().equals(owner)) {
                return employee;
            }
        }
        return null;
    }
}
