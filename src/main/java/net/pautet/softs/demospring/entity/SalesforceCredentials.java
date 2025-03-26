package net.pautet.softs.demospring.entity;

public record SalesforceCredentials(String salesforceAccessToken, String instanceUrl, String dataCloudAccessToken,
                                    String dataCloudInstanceUrl) {

}
