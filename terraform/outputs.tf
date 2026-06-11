# ─────────────────────────────────────────────
# ReviewFlow — Root Outputs
# Values printed after terraform apply.
# Add these to GitHub Secrets after first apply.
# ─────────────────────────────────────────────

output "ec2_public_ip" {
  description = "Public IP of the EC2 instance — add to GitHub Secrets as EC2_HOST_STAGING or EC2_HOST_PROD"
  value       = module.ec2.public_ip
}

output "ec2_public_dns" {
  description = "Public DNS of the EC2 instance"
  value       = module.ec2.public_dns
}

output "ecr_repository_url" {
  description = "ECR repository URL — used in docker-compose.prod.yml and CI/CD pipeline"
  value       = module.ecr.repository_url
}

output "s3_bucket_name" {
  description = "S3 bucket name for ReviewFlow file storage"
  value       = module.s3.bucket_name
}

output "s3_bucket_arn" {
  description = "S3 bucket ARN"
  value       = module.s3.bucket_arn
}

output "cloudwatch_log_group_app" {
  description = "CloudWatch log group for application logs"
  value       = module.cloudwatch.log_group_app
}

output "cloudwatch_log_group_security" {
  description = "CloudWatch log group for security logs"
  value       = module.cloudwatch.log_group_security
}

output "sns_topic_arn" {
  description = "SNS topic ARN for alerts — subscribe additional emails if needed"
  value       = module.cloudwatch.sns_topic_arn
}

output "ec2_instance_id" {
  description = "EC2 instance ID — used in CloudWatch dashboards"
  value       = module.ec2.instance_id
}

output "next_steps" {
  description = "What to do after terraform apply"
  value       = <<-EOT

    ── After terraform apply ──────────────────────────────────

    1. Add EC2 IP to GitHub Secrets:
       EC2_HOST_STAGING = ${module.ec2.public_ip}   (staging)
       EC2_HOST_PROD    = ${module.ec2.public_ip}   (prod)

    2. Update Cloudflare DNS:
       staging.reviewflowlms.com → A → ${module.ec2.public_ip}
       reviewflowlms.com         → A → ${module.ec2.public_ip}

    3. SSH into EC2 and run first-time setup:
       ssh -i ~/.ssh/${var.ec2_key_name}.pem ec2-user@${module.ec2.public_ip}
       sudo /opt/reviewflow/setup.sh

    4. Trigger first deploy from GitHub Actions:
       Actions → CD — Deploy → Run workflow → staging

    5. Verify:
       curl https://staging.reviewflowlms.com/actuator/health

    ──────────────────────────────────────────────────────────
  EOT
}
output "ci_user_access_key_id" {
  description = "Add to GitHub Secrets as AWS_ACCESS_KEY_ID"
  value       = module.iam.ci_user_access_key_id
  sensitive   = true
}

output "ci_user_secret_access_key" {
  description = "Add to GitHub Secrets as AWS_SECRET_ACCESS_KEY"
  value       = module.iam.ci_user_secret_access_key
  sensitive   = true
}


