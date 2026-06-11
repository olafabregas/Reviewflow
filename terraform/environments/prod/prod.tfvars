# ─────────────────────────────────────────────
# ReviewFlow — Production Environment
#
# Usage:
#   terraform init
#   terraform plan -var-file="environments/prod/prod.tfvars"
#   terraform apply -var-file="environments/prod/prod.tfvars"
# ─────────────────────────────────────────────

environment    = "prod"
aws_region     = "ca-central-1"

# EC2
instance_type  = "t3.micro"             # Upgrade to t3.small when first client signs
ec2_key_name   = "reviewflow-prod"      # Separate key pair from staging
app_port       = 8082                   # Different port from staging on same EC2

# S3
s3_bucket_name = "reviewflow-storage"

# ECR — images tagged prod-{date}-{sha}
ecr_repository_name    = "reviewflow"
ecr_image_retain_count = 10             # Keep all prod images (safety)

# CloudWatch
alert_email        = "roqeeb@reviewflowlms.com"    # Replace with your email
log_retention_days = 30                             # 30 days app, 90 days security (overridden in module)
