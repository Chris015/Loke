package loke.config;

import loke.model.Admin;

import java.util.ArrayList;
import java.util.List;

public class Configuration {
    private boolean dryRun;
    private String accessKey;
    private String secretAccessKey;
    private String region;
    private String host;
    private int port;
    private String stagingDir;
    private String userOwnerRegExp;
    private String fromEmailAddress;
    private String toEmailDomainName;
    private double showAccountThreshold;
    private List<Admin> admins;

    public boolean isDryRun() {
        return dryRun;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public String getRegion() {
        return region;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getStagingDir() {
        return stagingDir;
    }

    public String getUserOwnerRegExp() {
        return userOwnerRegExp;
    }

    public String getFromEmailAddress() {
        return fromEmailAddress;
    }

    public String getToEmailDomainName() {
        return toEmailDomainName;
    }

    public double getShowAccountThreshold() {
        return showAccountThreshold;
    }

    public List<Admin> getAdmins() {
       return this.admins;
    }

    public void setAdmins(List<Admin> admins) {
        this.admins = admins;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public void setUserOwnerRegExp(String userOwnerRegExp) {
        this.userOwnerRegExp = userOwnerRegExp;
    }
}
