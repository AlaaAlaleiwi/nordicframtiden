# Deploy the backend to Google Cloud Run

The production service name and region used by the frontend are:

- Service: `nordicframtiden-api`
- Region: `europe-north1`
- Container port: provided automatically through `PORT`

The service must be in the same Google Cloud project as Firebase Hosting.

## Prerequisites

1. Create or select a Firebase project. Every Firebase project is also a Google Cloud project.
2. Install and authenticate the Google Cloud CLI.
3. Provide a production PostgreSQL database and Redis instance that Cloud Run can reach.
4. Store the database credentials and JWT secret in Secret Manager. Do not commit them to either repository.

Required secrets:

- `app-jwt-secret`
- `database-url`
- `database-username`
- `database-password`

Optional mail secret:

- `mail-password`

Optional Skatteverket FOSIK secrets (required when the integration is enabled):

- `skatteverket-tax-client-id`
- `skatteverket-tax-client-secret`

The Cloud Run runtime service account needs `Secret Manager Secret Accessor` for these secrets. If PostgreSQL or Redis has a private address, also configure Direct VPC egress for the service.

## Deploy

Set the active project and enable the required services:

```bash
gcloud config set project YOUR_PROJECT_ID
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com
```

From this backend repository, deploy the existing Dockerfile:

```bash
gcloud run deploy nordicframtiden-api \
  --source . \
  --region europe-north1 \
  --allow-unauthenticated \
  --max-instances 10 \
  --set-env-vars SPRING_FLYWAY_ENABLED=true,REDIS_HOST=YOUR_REDIS_HOST,REDIS_PORT=6379 \
  --set-secrets APP_JWT_SECRET=app-jwt-secret:latest,SPRING_DATASOURCE_URL=database-url:latest,SPRING_DATASOURCE_USERNAME=database-username:latest,SPRING_DATASOURCE_PASSWORD=database-password:latest,MAIL_PASSWORD=mail-password:latest
```

`--allow-unauthenticated` is required so Firebase Hosting can forward requests to the service. Spring Security still protects the application endpoints with JWT authorization.

If mail is not configured, omit `MAIL_PASSWORD=mail-password:latest` and supply the non-secret mail settings only when needed. For a private Redis or PostgreSQL endpoint, add the appropriate `--network`, `--subnet`, and `--vpc-egress` options to the deployment.

After Cloud Run reports a successful revision, deploy Firebase Hosting from the frontend repository. The Hosting deployment checks that this service already exists.

## Audio-call relay

WebRTC requires a publicly reachable TURN service when a direct connection is blocked by
NAT or a firewall. Cloud Run cannot expose the UDP port range required by coturn, so run
the included coturn service on a VM with a public IP, or use a managed TURN provider.

Configure the backend with the shared TURN values. The authenticated ICE endpoint returns
them to both the iOS and web clients:

```text
CALL_TURN_URLS=turn:TURN_HOST:3478,turn:TURN_HOST:3478?transport=tcp
CALL_TURN_USERNAME=<turn-username>
CALL_TURN_CREDENTIAL=<turn-password>
```

Store `CALL_TURN_CREDENTIAL` in Secret Manager in production.

## Skatteverket tax calculation

The backend can calculate monthly preliminary tax through Skatteverket's FOSIK API instead
of requiring every annual salary interval to be imported. It is disabled by default, so the
existing `tax_table_row` data remains the fallback until API access has been approved.

Configure the sandbox first:

```text
SKATTEVERKET_TAX_ENABLED=true
SKATTEVERKET_TAX_BASE_URL=https://api.test.skatteverket.se/inkomstbeskattning/fraga-om-skatteavdrag-i-kronor/v1
SKATTEVERKET_TAX_CLIENT_ID=<secret>
SKATTEVERKET_TAX_CLIENT_SECRET=<secret>
```

For production, omit `SKATTEVERKET_TAX_BASE_URL` to use the production default. Store both
credentials in Secret Manager and expose them to Cloud Run as environment variables. Never
put these credentials in an iOS or web client. Municipality-to-table mappings remain local
and versioned by tax year.

### Automatic public tax-table import

No API credentials are required for the public tax-table importer. It runs after
application startup and daily at 03:15 Europe/Stockholm time. During December it
also checks for the following year's tables. Both the monthly tables and all six
columns of the annual `engångstabell` are downloaded and validated independently
before the existing rows for that year are replaced transactionally.

Optional environment variables:

```text
SKATTEVERKET_TAX_TABLE_IMPORT_ENABLED=true
SKATTEVERKET_TAX_TABLE_IMPORT_CRON=0 15 3 * * *
SKATTEVERKET_TAX_TABLE_IMPORT_TIMEOUT_SECONDS=30
```

# Firebase chat notifications

Chat push notifications are optional and disabled by default. The frontend reads the
public Firebase web configuration from the authenticated backend; no Firebase server
credentials or private VAPID key are sent to the browser.

Configure these Cloud Run environment variables on the backend service:

```text
APP_FIREBASE_ENABLED=true
APP_FIREBASE_PROJECT_ID=<firebase-project-id>
APP_FIREBASE_WEB_API_KEY=<firebase-web-api-key>
APP_FIREBASE_AUTH_DOMAIN=<firebase-auth-domain>
APP_FIREBASE_STORAGE_BUCKET=<firebase-storage-bucket>
APP_FIREBASE_MESSAGING_SENDER_ID=<firebase-sender-id>
APP_FIREBASE_APP_ID=<firebase-web-app-id>
APP_FIREBASE_VAPID_PUBLIC_KEY=<firebase-web-push-public-key>
```

The Cloud Run runtime service account must have permission to send Firebase Cloud
Messaging messages in `APP_FIREBASE_PROJECT_ID`. The backend uses Application Default
Credentials; do not add a service-account JSON key to the repository or container.

Push payloads are intentionally generic. Firebase receives the installation target and
the text “You have a new message”, but not the sender, message body, room ID, or chat URL.
Users opt in with the bell button in Messages, and each browser installation is stored in
`chat_push_subscription` by Flyway migration `V22`.
