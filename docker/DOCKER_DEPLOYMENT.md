# ReviewFlow — Docker Deployment Guide

---

## Directory Structure on EC2

```
/opt/reviewflow/
├── docker-compose.staging.yml
├── docker-compose.prod.yml
├── .env                          ← secrets (never committed)
├── .env.example                  ← template (committed)
├── init/
│   └── 01_create_db.sql          ← MySQL first-boot init
├── mysql/
│   ├── staging.cnf               ← MySQL tuning (staging)
│   └── prod.cnf                  ← MySQL tuning (prod)
└── backups/                      ← MySQL dumps land here
```

---

## Required .env Values

Add these two new values to your existing `.env`:

```bash
# MySQL root password (separate from app user password)
# Used by: MySQL container, manual backup commands
DB_ROOT_PASSWORD=generate_with_openssl_rand_base64_24
```

Your existing `DB_PASSWORD` is the app user password.
`DB_ROOT_PASSWORD` is the MySQL root password — separate credential.

---

## First-Time Setup on EC2

```bash
# 1. Create directory structure
mkdir -p /opt/reviewflow/init
mkdir -p /opt/reviewflow/mysql
mkdir -p /opt/reviewflow/backups

# 2. Copy files from repo to EC2
# (CI/CD pipeline will handle this eventually —
#  for now, scp or create manually)

# 3. Generate DB_ROOT_PASSWORD and add to .env
openssl rand -base64 24
# Add to /opt/reviewflow/.env as DB_ROOT_PASSWORD=...

# 4. ECR login
aws ecr get-login-password --region ca-central-1 | \
  docker login --username AWS --password-stdin \
  797795454732.dkr.ecr.ca-central-1.amazonaws.com

# 5. Pull and start staging
docker-compose -f /opt/reviewflow/docker-compose.staging.yml up -d

# 6. Watch startup logs
docker-compose -f /opt/reviewflow/docker-compose.staging.yml logs -f

# 7. Verify health
curl http://localhost:8081/actuator/health
```

---

## How First Boot Works

```
docker-compose up
  → MySQL container starts
  → /docker-entrypoint-initdb.d/01_create_db.sql runs (first boot only)
      → creates 'reviewflow' database
      → creates 'reviewflow' user with DB_PASSWORD
      → grants minimum privileges
  → MySQL healthcheck passes (10s intervals, up to 10 retries)
  → App container starts (depends_on: mysql healthy)
  → Spring Boot starts
  → Flyway runs V1→V27 migrations automatically
  → Application ready
  → /actuator/health returns {"status":"UP"}
```

---

## Day-to-Day Operations

### Deploy new version (staging)
```bash
# CI/CD pipeline does this automatically on merge to main
# Manual equivalent:
aws ecr get-login-password --region ca-central-1 | \
  docker login --username AWS --password-stdin \
  797795454732.dkr.ecr.ca-central-1.amazonaws.com

docker pull 797795454732.dkr.ecr.ca-central-1.amazonaws.com/reviewflow:staging-latest
docker-compose -f /opt/reviewflow/docker-compose.staging.yml up -d --no-deps app
```

### Deploy new version (prod)
```bash
# CI/CD pipeline handles this — manual equivalent:
docker pull 797795454732.dkr.ecr.ca-central-1.amazonaws.com/reviewflow:prod-latest
docker-compose -f /opt/reviewflow/docker-compose.prod.yml up -d --no-deps app
```

### View logs
```bash
# App logs
docker logs reviewflow-app-staging -f --tail 100
docker logs reviewflow-app-prod -f --tail 100

# MySQL logs
docker logs reviewflow-mysql-staging -f --tail 50
docker logs reviewflow-mysql-prod -f --tail 50

# CloudWatch (structured JSON logs)
# View at: AWS Console → CloudWatch → Log groups → /reviewflow/application
```

### Restart app only (not MySQL)
```bash
docker-compose -f /opt/reviewflow/docker-compose.staging.yml restart app
```

### Check container status
```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

---

## Backup Procedure

**Run before every deployment to production:**

```bash
# Dump production database
docker exec reviewflow-mysql-prod mysqldump \
  -u root -p${DB_ROOT_PASSWORD} \
  --single-transaction \
  --routines \
  --triggers \
  reviewflow > /opt/reviewflow/backups/backup-$(date +%Y%m%d-%H%M%S).sql

# Verify dump size (should be non-zero)
ls -lh /opt/reviewflow/backups/

# Optional: copy to S3 for offsite backup
aws s3 cp /opt/reviewflow/backups/backup-$(date +%Y%m%d)*.sql \
  s3://reviewflow-storage/backups/
```

---

## Rollback Procedure

### App rollback (previous Docker image)
```bash
# Find the previous prod image tag
aws ecr describe-images \
  --repository-name reviewflow \
  --region ca-central-1 \
  --query 'imageDetails[?starts_with(imageTags[0], `prod-`)] | sort_by(@, &imagePushedAt) | [-2].imageTags[0]' \
  --output text

# Update image tag in compose file and restart
# Edit docker-compose.prod.yml → change prod-latest to prod-{previous-tag}
docker-compose -f /opt/reviewflow/docker-compose.prod.yml up -d --no-deps app
```

### Database rollback (restore from backup)
```bash
# ⚠️ Only do this if a migration caused data corruption
# Stop the app first
docker-compose -f /opt/reviewflow/docker-compose.prod.yml stop app

# Restore from backup
docker exec -i reviewflow-mysql-prod mysql \
  -u root -p${DB_ROOT_PASSWORD} reviewflow \
  < /opt/reviewflow/backups/backup-YYYYMMDD-HHMMSS.sql

# Restart app (Flyway will re-run if needed)
docker-compose -f /opt/reviewflow/docker-compose.prod.yml start app
```

---

## Troubleshooting

### App won't start — "Communications link failure"
MySQL isn't ready yet. Check:
```bash
docker logs reviewflow-mysql-staging --tail 20
# Look for: ready for connections
```

### App won't start — "Access denied for user 'reviewflow'"
Init script didn't run or DB_PASSWORD mismatch. Check:
```bash
docker exec -it reviewflow-mysql-staging mysql -u root -p
# Enter DB_ROOT_PASSWORD
SHOW GRANTS FOR 'reviewflow'@'%';
```

### Flyway migration failed
```bash
docker logs reviewflow-app-staging | grep -i flyway
# Common fix: check V-number continuity in src/main/resources/db/migration/
```

### Out of disk space
```bash
df -h
# Clean unused Docker images
docker image prune -a --filter "until=72h"
# Check log sizes
du -sh /var/log/reviewflow/
```
