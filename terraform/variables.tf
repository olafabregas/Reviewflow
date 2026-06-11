# ─────────────────────────────────────────────
# ReviewFlow — Root Variables
# All values supplied via environment-specific
# .tfvars files in environments/staging/ or
# environments/prod/
# ─────────────────────────────────────────────

variable "environment" {
  description = "Deployment environment (staging or prod)"
  type        = string

  validation {
    condition     = contains(["staging", "prod"], var.environment)
    error_message = "Environment must be 'staging' or 'prod'."
  }
}

variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "ca-central-1"
}

variable "vpc_id" {
  description = "VPC ID to deploy into. Uses default VPC if not specified."
  type        = string
  default     = ""
}

# ─── EC2 ───────────────────────────────────

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.micro"

  validation {
    condition     = contains(["t3.micro", "t3.small", "t3.medium"], var.instance_type)
    error_message = "Instance type must be t3.micro, t3.small, or t3.medium."
  }
}

variable "ec2_key_name" {
  description = "Name of the EC2 key pair for SSH access"
  type        = string
}

variable "app_port" {
  description = "Port the Spring Boot application listens on"
  type        = number
  default     = 8081

  validation {
    condition     = var.app_port > 1024 && var.app_port < 65535
    error_message = "App port must be between 1024 and 65535."
  }
}

# ─── S3 ────────────────────────────────────

variable "s3_bucket_name" {
  description = "Name of the S3 bucket for ReviewFlow file storage"
  type        = string
  default     = "reviewflow-storage"
}

# ─── ECR ───────────────────────────────────

variable "ecr_repository_name" {
  description = "Name of the ECR repository for Docker images"
  type        = string
  default     = "reviewflow"
}

variable "ecr_image_retain_count" {
  description = "Number of images to retain per environment tag prefix"
  type        = number
  default     = 10
}

# ─── CloudWatch ────────────────────────────

variable "alert_email" {
  description = "Email address for CloudWatch alarm notifications"
  type        = string
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 30

  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 180, 365], var.log_retention_days)
    error_message = "Log retention must be a valid CloudWatch retention value."
  }
}
