package loke.config;

import loke.model.Admin;

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
    private double generateReportThreshold;
    private List<Admin> admins;
    private boolean sendOnlyAdminReport;
    private String zipFileSourceBucket;
    private String gzFileDestinationBucket;
    private String sqlDatabaseName;
    private String sqlTableName;

    public String getZipFileSourceBucket() {
        return zipFileSourceBucket;
    }

    public String getGzFileDestinationBucket() {
        return gzFileDestinationBucket;
    }

    public void setSendOnlyAdminReport(boolean sendOnlyAdminReport) {
        this.sendOnlyAdminReport = sendOnlyAdminReport;
    }


    public boolean isSendOnlyAdminReport() {
        return sendOnlyAdminReport;
    }


    public boolean isDryRun() {
        return dryRun;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
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

    public void setUserOwnerRegExp(String userOwnerRegExp) {
        this.userOwnerRegExp = userOwnerRegExp;
    }

    public String getFromEmailAddress() {
        return fromEmailAddress;
    }

    public String getToEmailDomainName() {
        return toEmailDomainName;
    }

    public double getGenerateReportThreshold() {
        return generateReportThreshold;
    }

    public List<Admin> getAdmins() {
        return this.admins;
    }

    public void setAdmins(List<Admin> admins) {
        this.admins = admins;
    }

    public String getSqlDatabaseName() {
        return sqlDatabaseName;
    }

    public void setSqlDatabaseName(String sqlDatabaseName) {
        this.sqlDatabaseName = sqlDatabaseName;
    }

    public String getSqlTableName() {
        return sqlTableName;
    }

    public void setSqlTableName(String sqlTableName) {
        this.sqlTableName = sqlTableName;
    }
}
