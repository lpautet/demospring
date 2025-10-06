package net.pautet.softs.demospring.entity;

public record SalesforceCredentials(Long salesforceAccessTokenExpiresAt,
                                    SalesforceTokenResponse salesforceApiTokenResponse,
                                    DatacloudTokenResponse datacloudTokenResponse,
                                    Long dataCloudAccessTokenExpiresAt,
                                    String dataCloudInstanceUrl) {

    public SalesforceCredentials(SalesforceCredentials salesforceCredentials, long salesforceAccessTokenExpiresAt, SalesforceTokenResponse salesforceApiTokenResponse) {
        this(salesforceAccessTokenExpiresAt, salesforceApiTokenResponse, salesforceCredentials.datacloudTokenResponse, salesforceCredentials.dataCloudAccessTokenExpiresAt, salesforceCredentials.dataCloudInstanceUrl);
    }

    public SalesforceCredentials(SalesforceCredentials salesforceCredentials, DatacloudTokenResponse datacloudTokenResponse) {
        this(salesforceCredentials.salesforceAccessTokenExpiresAt, salesforceCredentials.salesforceApiTokenResponse(), datacloudTokenResponse, System.currentTimeMillis() - 60000 + 1000 * datacloudTokenResponse.expiresIn(),  "https://" + datacloudTokenResponse.instanceUrl());
    }

    public SalesforceCredentials() {
        this(null, null, null, null, null);
    }
}
