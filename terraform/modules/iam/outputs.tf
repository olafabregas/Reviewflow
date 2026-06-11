output "ec2_instance_profile_name" {
  value = aws_iam_instance_profile.ec2_profile.name
}

output "ci_user_access_key_id" {
  description = "Add to GitHub Secrets as AWS_ACCESS_KEY_ID"
  value       = aws_iam_access_key.ci_user.id
  sensitive   = true
}

output "ci_user_secret_access_key" {
  description = "Add to GitHub Secrets as AWS_SECRET_ACCESS_KEY"
  value       = aws_iam_access_key.ci_user.secret
  sensitive   = true
}
