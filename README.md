
DataCloud setup / Data Management / Ingestion API
* new connector
* use yaml file provided

Data Cloud / Data Streams
New Data Stream
* Ingestion API
* * select connector and schema
** category : Engegement
* * Primary Key (device Id)
* Event Time Field (timestamp)

configure salesforce access:
create key:
openssl genrsa 2048 > .private.key
openssl req -new -x509 -nodes -sha256 -days 365 -key .private.key -out server.crt
set SF_PRIVATE_KEY as :
awk 'BEGIN{RS=EOF} {gsub(/\n/,"",$0); print $0}' .private.key

Create a new External Client App
OAUTH
scope: cdp_ingest_api, cdp_api, api, refresh_token, offline_access
callback_url put anything, not used
check Enable JWT Bearer Flow and upload certificate

once created retrieve consumer key and secret
consumer key is SF_CLIENT_ID in .env


launch
source .env && mvn spring-boot:run    