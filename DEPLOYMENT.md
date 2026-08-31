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
