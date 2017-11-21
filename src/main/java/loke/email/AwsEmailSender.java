package loke.email;

import loke.model.Admin;
import loke.model.Employee;
import loke.model.Report;
import loke.model.TotalReport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class AwsEmailSender {
    private static final Logger log = LogManager.getLogger(AwsEmailSender.class);
    private AwsSesHandler awsSesHandler;
    private String toEmailDomainName;
    private String subject = "Weekly AWS cost report";
    private String from;
    private boolean dryRun;

    public AwsEmailSender(AwsSesHandler awsSesHandler, String from, String toEmailDomainName, boolean dryRun) {
        this.awsSesHandler = awsSesHandler;
        this.from = from;
        this.toEmailDomainName = toEmailDomainName;
        this.dryRun = dryRun;
    }

    public void sendEmployeeMails(List<Employee> employeeReports) {
        log.info("Sending emails to employees");
        for (Employee employee : employeeReports) {
            StringBuilder htmlBody = new StringBuilder();
            log.info("Creating email for: {}", employee.getUserName());
            String to = employee.getUserName() + toEmailDomainName;
            for (Report report : employee.getReports()) {
                addChartUrl(htmlBody, report);
                addHtmlTable(htmlBody, report);
            }
            if (dryRun) {
                log.info("DryRun: Email not sent to: {}", employee.getUserName());
                log.trace("Email for {}: {}", employee.getUserName(), htmlBody.toString().trim());
                return;
            }
            if (htmlBody.length() > 0) {
                awsSesHandler.sendEmail(to, htmlBody.toString().trim(), subject, from);
            }
        }
    }

    public void sendAdminMails(List<Admin> admins, List<Employee> adminReports) {
        if (admins == null || admins.size() == 0) {
            throw new IllegalArgumentException("No admins specified in the configuration file");
        }

        log.info("Sending emails to administrators");
        StringBuilder htmlBody = new StringBuilder();
        for (Employee employee : adminReports) {
            log.info("Adding reports for: {} to html body", employee.getUserName());
            for (Report report : employee.getReports()) {
                if (report instanceof TotalReport) {
                    addChartUrl(htmlBody, report);
                }
                addHtmlTable(htmlBody, report);
            }
        }
        if (dryRun) {
            log.info("DryRun: Emails not sent\nAdmin-mail: {}", htmlBody.toString().trim());
            return;
        }

        if (htmlBody.length() > 0) {
            for (Admin admin : admins) {
                awsSesHandler.sendEmail(admin.getEmailAddress(), htmlBody.toString().trim(), subject, from);
            }
        } else {
            log.info("No admin emails were sent. HtmlBody size: {}", htmlBody.length());
        }
    }

    private void addChartUrl(StringBuilder htmlBody, Report report) {
        if (report.getChartUrl() != null) {
            htmlBody.append("<img src=\"").append(report.getChartUrl()).append("\"/img>");
            htmlBody.append("<br>");
        }
    }

    private void addHtmlTable(StringBuilder htmlBody, Report report) {
        if (report.getHtmlTable() != null) {
            htmlBody.append(report.getHtmlTable());
            htmlBody.append("<br>");
        }
    }
}
