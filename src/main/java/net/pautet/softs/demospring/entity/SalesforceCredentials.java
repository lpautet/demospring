package net.pautet.softs.demospring.entity;

public record SalesforceCredentials(Long salesforceAccessTokenExpiresAt,
                                 SalesforceTokenResponse salesforceApiTokenResponse,
                                    String dataCloudAccessToken,
                                    Long dataCloudAccessTokenExpiresAt,
                                    String dataCloudInstanceUrl) {

    public SalesforceCredentials(SalesforceCredentials salesforceCredentials, long salesforceAccessTokenExpiresAt, SalesforceTokenResponse salesforceApiTokenResponse ) {
        this(salesforceAccessTokenExpiresAt,salesforceApiTokenResponse, salesforceCredentials.dataCloudAccessToken, salesforceCredentials.dataCloudAccessTokenExpiresAt, salesforceCredentials.dataCloudInstanceUrl);
    }

    public SalesforceCredentials(SalesforceCredentials salesforceCredentials,  String dataCloudAccessToken,
                                 long dataCloudAccessTokenExpiresAt,
                                 String dataCloudInstanceUrl) {
        this(salesforceCredentials.salesforceAccessTokenExpiresAt, salesforceCredentials.salesforceApiTokenResponse(), dataCloudAccessToken, dataCloudAccessTokenExpiresAt, dataCloudInstanceUrl);
    }

    public SalesforceCredentials() {
        this(null, null, null, null, null );
    }
}
