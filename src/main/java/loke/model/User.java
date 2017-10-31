package loke.model;

import java.util.ArrayList;
import java.util.List;

public class User {
    private String userName;
    private List<Report> reports;

    public User(String userName) {
        this.userName = userName;
        this.reports = new ArrayList<>();
    }

    public String getUserName() {
        return userName;
    }

    public List<Report> getReports() {
        return reports;
    }

    public void addReports(List<Report> reports) {
        this.reports.addAll(reports);
    }

}
