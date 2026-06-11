#!/bin/bash
# ─────────────────────────────────────────────
# ReviewFlow EC2 First-Boot Setup Script
# Environment: ${environment}
# Runs once on instance launch via user_data.
# All subsequent deploys use the CI/CD pipeline.
# ─────────────────────────────────────────────

set -euxo pipefail
exec > >(tee /var/log/user-data.log) 2>&1

echo "=== ReviewFlow EC2 Bootstrap — ${environment} ==="
echo "Started at: $(date)"

# ── System update ────────────────────────────
dnf update -y
dnf install -y \
  docker \
  git \
  curl \
  wget \
  unzip \
  jq \
  mysql \
  htop

# ── Docker ───────────────────────────────────
systemctl start docker
systemctl enable docker
usermod -aG docker ec2-user

# ── Docker Compose ───────────────────────────
DOCKER_COMPOSE_VERSION="2.24.0"
curl -SL "https://github.com/docker/compose/releases/download/v$${DOCKER_COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose
ln -sf /usr/local/bin/docker-compose /usr/bin/docker-compose

# ── AWS CLI v2 ───────────────────────────────
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp
/tmp/aws/install
rm -rf /tmp/awscliv2.zip /tmp/aws

# ── CloudWatch Agent ─────────────────────────
wget -q https://s3.amazonaws.com/amazoncloudwatch-agent/amazon_linux/amd64/latest/amazon-cloudwatch-agent.rpm \
  -O /tmp/cloudwatch-agent.rpm
rpm -U /tmp/cloudwatch-agent.rpm
rm /tmp/cloudwatch-agent.rpm

# CloudWatch agent config
cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json << 'CWCONFIG'
{
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/var/log/reviewflow/application.log",
            "log_group_name": "/reviewflow/application",
            "log_stream_name": "${environment}-{instance_id}",
            "retention_in_days": 30,
            "timezone": "UTC"
          },
          {
            "file_path": "/var/log/reviewflow/security.log",
            "log_group_name": "/reviewflow/security",
            "log_stream_name": "${environment}-{instance_id}",
            "retention_in_days": 90,
            "timezone": "UTC"
          }
        ]
      }
    }
  },
  "metrics": {
    "namespace": "ReviewFlow/${environment}",
    "metrics_collected": {
      "cpu": {
        "measurement": ["cpu_usage_idle", "cpu_usage_user", "cpu_usage_system"],
        "metrics_collection_interval": 60
      },
      "mem": {
        "measurement": ["mem_used_percent"],
        "metrics_collection_interval": 60
      },
      "disk": {
        "measurement": ["disk_used_percent"],
        "metrics_collection_interval": 300,
        "resources": ["/"]
      }
    }
  }
}
CWCONFIG

systemctl start amazon-cloudwatch-agent
systemctl enable amazon-cloudwatch-agent

# ── App directory structure ───────────────────
mkdir -p /opt/reviewflow
mkdir -p /var/log/reviewflow
chown -R ec2-user:ec2-user /opt/reviewflow
chown -R ec2-user:ec2-user /var/log/reviewflow

# ── docker-compose.prod.yml placeholder ──────
# Real file is deployed by CI/CD pipeline.
# This placeholder prevents docker-compose errors
# if someone runs it before first deploy.
cat > /opt/reviewflow/docker-compose.prod.yml << 'COMPOSE'
# This file is managed by the CI/CD pipeline.
# Do not edit manually — changes will be overwritten on deploy.
# To update: push to main branch and the pipeline will redeploy.
version: '3.8'
services:
  app:
    image: ${ecr_repository_url}:staging-latest
    ports:
      - "${app_port}:8081"
    env_file:
      - /opt/reviewflow/.env
    volumes:
      - /var/log/reviewflow:/var/log/reviewflow
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "-q", "-O", "/dev/null", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD_FILE: /run/secrets/db_root_password
      MYSQL_DATABASE: reviewflow
      MYSQL_USER: reviewflow
      MYSQL_PASSWORD_FILE: /run/secrets/db_password
    volumes:
      - mysql_data:/var/lib/mysql
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 10
volumes:
  mysql_data:
COMPOSE

chown ec2-user:ec2-user /opt/reviewflow/docker-compose.prod.yml

# ── .env placeholder ─────────────────────────
# NEVER committed to git.
# Populated manually after EC2 launch.
cat > /opt/reviewflow/.env.example << 'ENVEXAMPLE'
# Copy this to .env and fill in real values
# Never commit .env to version control

SPRING_PROFILES_ACTIVE=prod

# Database
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/reviewflow
SPRING_DATASOURCE_USERNAME=reviewflow
SPRING_DATASOURCE_PASSWORD=

# Auth
JWT_SECRET=
JWT_ACCESS_TOKEN_EXPIRY_MINUTES=15
JWT_REFRESH_TOKEN_EXPIRY_DAYS=7

# Hashids — NEVER change after first deployment
HASHIDS_SALT=
HASHIDS_MIN_LENGTH=8

# S3
AWS_S3_BUCKET=${s3_bucket_name}
AWS_REGION=${aws_region}
# No AWS keys needed — EC2 role handles auth

# Email (Resend SMTP)
RESEND_SMTP_HOST=smtp.resend.com
RESEND_SMTP_PORT=587
RESEND_SMTP_USERNAME=resend
RESEND_SMTP_PASSWORD=
MAIL_FROM=noreply@reviewflowlms.com

# Security headers
SECURITY_HEADERS_HSTS_ENABLED=true
SECURITY_HEADERS_HSTS_MAX_AGE=31536000
SECURITY_HEADERS_HSTS_INCLUDE_SUBDOMAINS=true

# CORS
CORS_ALLOWED_ORIGINS=https://reviewflowlms.com

# Monitoring
CLOUDWATCH_METRICS_ENABLED=false
ENVEXAMPLE

chown ec2-user:ec2-user /opt/reviewflow/.env.example

echo "=== Bootstrap complete at: $(date) ==="
echo "=== Next steps: ==="
echo "  1. Copy /opt/reviewflow/.env.example to /opt/reviewflow/.env"
echo "  2. Fill in all values in .env"
echo "  3. Trigger first deploy from GitHub Actions"
