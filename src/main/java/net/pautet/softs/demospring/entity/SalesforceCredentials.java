package net.pautet.softs.demospring.entity;

public record SalesforceCredentials(Long salesforceAccessTokenExpiresAt,
                                    String salesforceAccessToken,
                                    String salesforceInstanceUrl,
                                    String salesforceUserId,
                                    String dataCloudAccessToken,
                                    Long dataCloudAccessTokenExpiresAt,
                                    String dataCloudInstanceUrl) {

    public SalesforceCredentials(SalesforceCredentials salesforceCredentials, long salesforceAccessTokenExpiresAt, String salesforceAccessToken, String salesforceUserId,
                                 String salesforceInstanceUrl ) {
        this(salesforceAccessTokenExpiresAt, salesforceAccessToken, salesforceInstanceUrl, salesforceUserId, salesforceCredentials.dataCloudAccessToken, salesforceCredentials.dataCloudAccessTokenExpiresAt, salesforceCredentials.dataCloudInstanceUrl);
    }

    public SalesforceCredentials(SalesforceCredentials salesforceCredentials,  String dataCloudAccessToken,
                                 long dataCloudAccessTokenExpiresAt,
                                 String dataCloudInstanceUrl) {
        this(salesforceCredentials.salesforceAccessTokenExpiresAt, salesforceCredentials.salesforceAccessToken, salesforceCredentials.salesforceInstanceUrl, salesforceCredentials.salesforceUserId, dataCloudAccessToken, dataCloudAccessTokenExpiresAt, dataCloudInstanceUrl);
    }

    public SalesforceCredentials() {
        this(null, null, null, null, null, null, null);
    }
}
