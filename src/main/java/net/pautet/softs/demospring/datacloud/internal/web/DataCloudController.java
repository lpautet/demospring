package net.pautet.softs.demospring.datacloud.internal.web;

import lombok.RequiredArgsConstructor;
import net.pautet.softs.demospring.datacloud.SalesforceService;
import net.pautet.softs.demospring.datacloud.SalesforceUserInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class DataCloudController {

    private final SalesforceService salesforceService;

    @GetMapping("/salesforce/accounts")
    String getAccounts() throws IOException {
        return salesforceService.fetchData();
    }

    @GetMapping("/datacloud/data")
    String getDataCloudData() throws IOException {
        return salesforceService.fetchDataCloudData();
    }

    @GetMapping("/salesforce/whoami")
    SalesforceUserInfo getSalesforceUser() throws IOException {
        return salesforceService.getSalesforceUser();
    }
}
