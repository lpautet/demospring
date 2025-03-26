package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.SalesforceCredentials;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class SalesforceService {

    private final SalesforceCredentials salesforceCredentials;

    public SalesforceService(SalesforceCredentials salesforceCredentials) {
        this.salesforceCredentials = salesforceCredentials;
    }

    public String fetchData() throws IOException {
        HttpClient httpClient = HttpClients.createDefault();
        String apiUrl = salesforceCredentials.instanceUrl() + "/services/data/v59.0/query?q=SELECT+Id,Name+FROM+Account+LIMIT+10";
        HttpGet get = new HttpGet(apiUrl);
        get.setHeader("Authorization", "Bearer " + salesforceCredentials.salesforceAccessToken());

        HttpResponse response = httpClient.execute(get);
        String responseBody = EntityUtils.toString(response.getEntity());
        log.debug("Salesforce fetch data response: {}", responseBody);
        return responseBody;
    }

    public String fetchDataCloudData() throws IOException {
        HttpClient httpClient = HttpClients.createDefault();
        String apiUrl = salesforceCredentials.dataCloudInstanceUrl() + "/api/v2/query";
        HttpPost post = new HttpPost(apiUrl);
        post.setHeader("Authorization", "Bearer " + salesforceCredentials.dataCloudAccessToken());
        post.setHeader("Content-Type", "application/json");

        // Example SQL query for Data Cloud (replace with your actual query)
        String query = "{\"sql\": \"SELECT * FROM Netatmo_Weather_Connector_Weath_8654E091__dll LIMIT 10\"}";
        post.setEntity(new StringEntity(query));

        HttpResponse response = httpClient.execute(post);
        String responseBody = EntityUtils.toString(response.getEntity());
        log.debug("Data Cloud fetch data response status: {}", response.getStatusLine());
        log.debug("Data Cloud fetch data response: {}", responseBody);
        return responseBody;
    }
}
