package net.pautet.softs.demospring.entity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesforceUserInfo {
    private String id;

    @JsonProperty("asserted_user")
    private boolean assertedUser;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("organization_id")
    private String organizationId;

    private String username;

    @JsonProperty("nick_name")
    private String nickName;

    @JsonProperty("display_name")
    private String displayName;

    private String email;

    @JsonProperty("email_verified")
    private boolean emailVerified;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String timezone;

    private Photos photos;

    @JsonProperty("addr_street")
    private String addrStreet;

    @JsonProperty("addr_city")
    private String addrCity;

    @JsonProperty("addr_state")
    private String addrState;

    @JsonProperty("addr_country")
    private String addrCountry;

    @JsonProperty("addr_zip")
    private String addrZip;

    @JsonProperty("mobile_phone")
    private String mobilePhone;

    @JsonProperty("mobile_phone_verified")
    private boolean mobilePhoneVerified;

    @JsonProperty("is_lightning_login_user")
    private boolean isLightningLoginUser;

    private Status status;

    private Urls urls;

    private boolean active;

    @JsonProperty("user_type")
    private String userType;

    private String language;

    private String locale;

    @JsonProperty("utcOffset")
    private long utcOffset;

    @JsonProperty("last_modified_date")
    private String lastModifiedDate;

    @JsonProperty("is_app_installed")
    private boolean isAppInstalled;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Photos {
        private String picture;
        private String thumbnail;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Status {
        @JsonProperty("created_date")
        private String createdDate;
        private String body;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Urls {
        private String enterprise;
        private String metadata;
        private String partner;
        private String rest;
        private String sobjects;
        private String search;
        private String query;
        private String recent;

        @JsonProperty("tooling_soap")
        private String toolingSoap;

        @JsonProperty("tooling_rest")
        private String toolingRest;

        private String profile;
        private String feeds;
        private String groups;
        private String users;

        @JsonProperty("feed_items")
        private String feedItems;

        @JsonProperty("feed_elements")
        private String feedElements;

        @JsonProperty("custom_domain")
        private String customDomain;
    }
}