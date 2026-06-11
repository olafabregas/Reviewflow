# ReviewFlow — Terraform Infrastructure

**Provider:** AWS (`ca-central-1`)  
**State:** S3 backend (`reviewflow-terraform-state-ca`) + DynamoDB lock (`reviewflow-terraform-locks`)  
**Environments:** `staging` + `prod` via separate `.tfvars`  
**DNS:** Cloudflare (managed manually — not in Terraform)  
**Last updated:** May 2026

---

## Directory Structure

```
terraform/
├── main.tf                          ← Root — wires all modules together
├── variables.tf                     ← Root variable definitions
├── outputs.tf                       ← Root outputs (IPs, URLs, next steps)
├── environments/
│   ├── staging/staging.tfvars       ← Staging-specific values
│   └── prod/prod.tfvars             ← Production-specific values
└── modules/
    ├── ec2/                         ← EC2 instance + Elastic IP
    │   ├── main.tf
    │   ├── variables.tf
    │   ├── outputs.tf
    │   └── templates/
    │       └── user_data.sh.tpl     ← First-boot bootstrap script
    ├── ecr/                         ← ECR repository + lifecycle policy
    ├── iam/                         ← EC2 role + CI/CD user + policies
    ├── s3/                          ← S3 bucket + encryption + CORS + lifecycle
    ├── security-groups/             ← EC2 security group (ingress/egress rules)
    └── cloudwatch/                  ← Log groups + 10 alarms + dashboard + SNS
```

---

## What Terraform Manages

| Resource | Module | Notes |
|---|---|---|
| EC2 t3.micro | `ec2` | Amazon Linux 2023, Docker pre-installed via user_data |
| Elastic IP | `ec2` | Stable IP — Cloudflare DNS never needs updating after restart |
| Security Group | `security-groups` | Ports 22, 80, 443, 8081/8082 |
| S3 bucket | `s3` | `reviewflow-storage`, encrypted, versioned, lifecycle rules |
| ECR repository | `ecr` | `reviewflow`, scan on push, lifecycle policy |
| EC2 IAM role | `iam` | S3 + CloudWatch + ECR pull — no hardcoded keys on EC2 |
| CI/CD IAM user | `iam` | ECR push/pull only — keys output for GitHub Secrets |
| CloudWatch log groups | `cloudwatch` | `/reviewflow/application` + `/reviewflow/security` |
| SNS alert topic | `cloudwatch` | Email alerts to `alert_email` |
| 10 CloudWatch alarms | `cloudwatch` | CPU, memory, disk, status, errors, auth failures, rate limits, blocked uploads, 5xx, health check |
| CloudWatch dashboard | `cloudwatch` | 6-widget operational overview |

## What Terraform Does NOT Manage

| Resource | Where it lives | Why |
|---|---|---|
| Cloudflare DNS records | Cloudflare dashboard | Manual — DNS provider is not AWS |
| `.env` file on EC2 | EC2 manually | Contains secrets — never in Terraform state |
| MySQL data | EC2 Docker volume | Stateful data — destroy would wipe student records |
| GitHub Secrets | GitHub UI | Not AWS resources |
| SSL certificate | Cloudflare (auto) | Cloudflare proxying provides TLS termination |
| Domain registration | Cloudflare | Not AWS |

---

## Bootstrap Status — What Already Exists

The following resources were created manually before Terraform.
**Do NOT recreate them.** Terraform will use them exactly as they are.

| Resource | Name | Status |
|---|---|---|
| S3 state bucket | `reviewflow-terraform-state-ca` | ✅ Already created |
| DynamoDB lock table | `reviewflow-terraform-locks` | ✅ Already created |
| EC2 key pair (staging) | `reviewflow-staging` | ✅ Already created |
| EC2 key pair (prod) | `reviewflow-prod` | ✅ Already created |

### Verify before first apply

Run these to confirm everything is accessible before touching Terraform:

```bash
# Confirm state bucket is reachable
aws s3 ls s3://reviewflow-terraform-state-ca

# Confirm DynamoDB lock table exists
aws dynamodb describe-table \
  --table-name reviewflow-terraform-locks \
  --region ca-central-1 \
  --query 'Table.TableStatus'
# Expected output: "ACTIVE"

# Confirm both key pairs exist
aws ec2 describe-key-pairs \
  --key-names reviewflow-staging reviewflow-prod \
  --region ca-central-1 \
  --query 'KeyPairs[].KeyName'
# Expected output: ["reviewflow-prod", "reviewflow-staging"]
```

### Verify and harden state bucket (do this once if not already done)

```bash
# Check versioning — should return "Enabled"
aws s3api get-bucket-versioning \
  --bucket reviewflow-terraform-state-ca \
  --query 'Status'

# If not enabled:
aws s3api put-bucket-versioning \
  --bucket reviewflow-terraform-state-ca \
  --versioning-configuration Status=Enabled

# Check encryption
aws s3api get-bucket-encryption \
  --bucket reviewflow-terraform-state-ca

# If not set:
aws s3api put-bucket-encryption \
  --bucket reviewflow-terraform-state-ca \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

# Block all public access (critical — state contains sensitive outputs)
aws s3api put-public-access-block \
  --bucket reviewflow-terraform-state-ca \
  --public-access-block-configuration \
  "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
```

---

## Usage

### Staging — First Run

```bash
cd terraform/

# Step 1 — Initialise (first time only)
# Downloads providers, connects to S3 backend
terraform init

# Expected output:
# Successfully configured the backend "s3"!
# Terraform has been successfully initialized!

# Step 2 — Preview what Terraform will create
terraform plan -var-file="environments/staging/staging.tfvars"

# Step 3 — Apply
terraform apply -var-file="environments/staging/staging.tfvars"

# Step 4 — Read outputs (add these to GitHub Secrets immediately)
terraform output ec2_public_ip                   # → EC2_HOST_STAGING in GitHub Secrets
terraform output ecr_repository_url              # → used in docker-compose.prod.yml
terraform output -raw ci_user_access_key_id      # → AWS_ACCESS_KEY_ID in GitHub Secrets
terraform output -raw ci_user_secret_access_key  # → AWS_SECRET_ACCESS_KEY in GitHub Secrets
```

### Production

```bash
cd terraform/

# Use a separate workspace to keep staging and prod state isolated
terraform workspace new prod
terraform workspace select prod

terraform plan -var-file="environments/prod/prod.tfvars"
terraform apply -var-file="environments/prod/prod.tfvars"
```

---

## After First terraform apply — Checklist

```
[ ] terraform output next_steps — read the printed instructions

[ ] Add to GitHub Secrets:
    AWS_ACCESS_KEY_ID     = terraform output -raw ci_user_access_key_id
    AWS_SECRET_ACCESS_KEY = terraform output -raw ci_user_secret_access_key
    EC2_HOST_STAGING      = terraform output ec2_public_ip (staging workspace)
    EC2_HOST_PROD         = terraform output ec2_public_ip (prod workspace)

[ ] Update Cloudflare DNS:
    staging.reviewflowlms.com → A → staging Elastic IP (Cloudflare proxy ON)
    reviewflowlms.com         → A → prod Elastic IP    (Cloudflare proxy ON)

[ ] SSH into EC2 and create .env:
    ssh -i ~/.ssh/reviewflow-staging.pem ec2-user@{EC2_IP}
    cp /opt/reviewflow/.env.example /opt/reviewflow/.env
    nano /opt/reviewflow/.env
    # Fill in: DB_PASSWORD, JWT_SECRET, HASHIDS_SALT,
    #          RESEND_SMTP_PASSWORD, SENTRY_DSN, and all other values

[ ] Confirm SNS email subscription:
    Check inbox for AWS SNS confirmation email
    Click "Confirm subscription" — alarms won't fire until confirmed

[ ] Trigger first deploy from GitHub Actions:
    Actions → CD — Deploy → Run workflow → staging

[ ] Verify deployment:
    curl https://staging.reviewflowlms.com/actuator/health
    # Expected: {"status":"UP","components":{...}}
```

---

## Key Design Decisions

**Why Elastic IP:**
EC2 instances get a new public IP on every stop/start. Without an Elastic IP, Cloudflare DNS would need manual updating after every restart. Elastic IP is free when attached to a running instance — costs $0.005/hr only when detached.

**Why `prevent_destroy` on S3:**
The S3 bucket contains student submissions, PDFs, and avatars. `terraform destroy` must never wipe this data. `prevent_destroy = true` makes Terraform refuse to delete the bucket — a deliberate friction point that requires removing the lifecycle block before destruction.

**Why `ignore_changes` on EC2 AMI:**
Amazon Linux releases new AMIs regularly. Without `ignore_changes = [ami]`, the next `terraform apply` after a new AMI release would replace the running EC2 instance, destroying the app. AMI updates are handled by launching a new instance, not in-place replacement.

**Why separate key pairs per environment:**
If `reviewflow-staging` is compromised, it cannot access production. Separate keys = separate blast radius.

**Why the CI user only gets ECR permissions:**
EC2 deploys happen via SSH in GitHub Actions — the SSH key is the auth mechanism. The CI IAM user only needs ECR push/pull. Giving it EC2 permissions would be overprivileged.

**Why 10 CloudWatch alarms specifically:**
CloudWatch free tier includes exactly 10 alarms. The 10 chosen cover: infrastructure health (CPU, memory, disk, instance status), application health (5xx, errors, health check), and security signals (auth failures, rate limits, blocked uploads).

---

## Staging vs Production Differences

| Config | Staging | Production |
|---|---|---|
| `app_port` | 8081 | 8082 |
| `instance_type` | t3.micro | t3.micro (→ t3.small at first client) |
| `log_retention_days` | 14 | 30 |
| `disable_api_termination` | false | true |
| EC2 key pair | `reviewflow-staging` | `reviewflow-prod` |
| ECR image tag prefix | `staging-{sha}` | `prod-{date}-{sha}` |
| Cloudflare DNS | `staging.reviewflowlms.com` | `reviewflowlms.com` |

Both environments run on the **same EC2 instance** on different ports.
MySQL runs once, shared — two separate databases (`reviewflow_staging`, `reviewflow_prod`).

---

## Destroying Infrastructure (when needed)

```bash
# Staging — safe to destroy specific modules
terraform destroy -var-file="environments/staging/staging.tfvars" \
  -target=module.ec2 \
  -target=module.cloudwatch

# NEVER run full terraform destroy on production without:
# 1. MySQL dump first:
ssh -i ~/.ssh/reviewflow-prod.pem ec2-user@{EC2_IP}
docker exec mysql mysqldump -u root -p reviewflow > /opt/reviewflow/backup-$(date +%Y%m%d).sql

# 2. The S3 bucket has prevent_destroy = true — it will block full destroy.
#    To actually remove the bucket, remove the lifecycle block from modules/s3/main.tf first.
#    This is deliberate — student data should never be accidentally destroyed.
```
