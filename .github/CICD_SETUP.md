# GitHub Actions CI/CD Setup

ReviewFlow uses three workflows in `.github/workflows/`:

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `ci.yml` | Every push; PRs to `master` | Compile, unit tests, integration tests (PR), Docker build verify (PR) |
| `cd.yml` | Push to `master`; manual dispatch | Full tests → ECR push → staging deploy; prod via manual approval |
| `nightly.yml` | Daily 02:00 UTC | OWASP full scan, Postman staging smoke, ECR cleanup |

## Required GitHub Secrets

| Secret | Used by | Source |
|--------|---------|--------|
| `AWS_ACCESS_KEY_ID` | CD, nightly | `terraform output -raw ci_user_access_key_id` |
| `AWS_SECRET_ACCESS_KEY` | CD, nightly | `terraform output -raw ci_user_secret_access_key` |
| `EC2_HOST_STAGING` | CD deploy-staging, nightly Postman | Staging Elastic IP |
| `EC2_SSH_KEY` | CD deploy-staging | PEM for `reviewflow-staging` key pair |
| `EC2_HOST_PROD` | CD deploy-production | Production Elastic IP |
| `EC2_SSH_KEY_PROD` | CD deploy-production | PEM for `reviewflow-prod` key pair |

`GITHUB_TOKEN` is provided automatically for JaCoCo PR comments.

## GitHub Environments

Create two environments in **Settings → Environments**:

- **staging** — optional protection rules
- **production** — require reviewers before deploy

## EC2 first-time setup

Copy compose files from this repo to the instance:

```bash
sudo mkdir -p /opt/reviewflow/{init,mysql,backups}
sudo cp docker/docker-compose.staging.yml /opt/reviewflow/
sudo cp docker/docker-compose.prod.yml /opt/reviewflow/
sudo cp -r docker/init docker/mysql /opt/reviewflow/
sudo cp docker/.env.example /opt/reviewflow/.env.example
# Fill in /opt/reviewflow/.env from .env.example, then:
docker-compose -f /opt/reviewflow/docker-compose.staging.yml up -d
```

See `docker/DOCKER_DEPLOYMENT.md` for full details.

## Docker image

- **Dockerfile** at repo root — multi-stage build (JDK 21 → JRE 21)
- **ECR tags:** `staging-{sha8}`, `staging-latest`, `prod-{date}-{sha8}`, `prod-latest`
- **Region:** `ca-central-1`
- **Repository:** `reviewflow`

## Local verify

```bash
docker build -t reviewflow:local .
docker compose -f docker/docker-compose.staging.yml config
```
