package net.pautet.softs.demospring.entity;

import java.util.Date;

public record SalesforceCredentials(Long salesforceAccessTokenExpiresAt,
                                    String salesforceAccessToken,
                                    String salesforceInstanceUrl,
                                    String dataCloudAccessToken,
                                    Long dataCloudAccessTokenExpiresAt,
                                    String dataCloudInstanceUrl) {

    public SalesforceCredentials(long salesforceAccessTokenExpiresAt, String salesforceAccessToken,
                                 String salesforceInstanceUrl ) {
        this(salesforceAccessTokenExpiresAt, salesforceAccessToken, salesforceInstanceUrl, null, null, null);
    }

    public SalesforceCredentials(SalesforceCredentials salesforceCredentials,  String dataCloudAccessToken,
                                 long dataCloudAccessTokenExpiresAt,
                                 String dataCloudInstanceUrl) {
        this(salesforceCredentials.salesforceAccessTokenExpiresAt, salesforceCredentials.salesforceAccessToken, salesforceCredentials.salesforceInstanceUrl, dataCloudAccessToken, dataCloudAccessTokenExpiresAt, dataCloudInstanceUrl);
    }
}
