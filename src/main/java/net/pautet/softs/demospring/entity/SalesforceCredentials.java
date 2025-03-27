package net.pautet.softs.demospring.entity;

public record SalesforceCredentials(String salesforceAccessToken,
                                    String salesforceInstanceUrl,
                                    String dataCloudAccessToken,
                                    Long dataCloudAccessTokenExpiresAt,
                                    String dataCloudInstanceUrl) {

}
