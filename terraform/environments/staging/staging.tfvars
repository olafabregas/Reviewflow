# ─────────────────────────────────────────────
# ReviewFlow — Staging Environment
#
# Usage:
#   terraform init
#   terraform plan -var-file="environments/staging/staging.tfvars"
#   terraform apply -var-file="environments/staging/staging.tfvars"
# ─────────────────────────────────────────────

environment    = "staging"
aws_region     = "ca-central-1"

# EC2
instance_type  = "t3.micro"
ec2_key_name   = "reviewflow-staging"   # Name of key pair in AWS — create before apply
app_port       = 8081

# S3 — staging uses same bucket with environment prefix in keys
# S3 key structure: submissions/{env}/{hashedAssignmentId}/...
s3_bucket_name = "reviewflow-storage"

# ECR — shared repository, images tagged staging-{sha}
ecr_repository_name    = "reviewflow"
ecr_image_retain_count = 10

# CloudWatch
alert_email        = "olamidefabregas26@gmail.com"    # Replace with your email
log_retention_days = 14                             # Shorter retention for staging
