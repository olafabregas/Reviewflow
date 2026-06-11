terraform {
  required_version = ">= 1.7.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # ─────────────────────────────────────────────
  # Remote state — S3 backend + DynamoDB lock
  # The state bucket and lock table are the ONLY
  # resources created manually before Terraform runs.
  # Everything else is managed by Terraform.
  #
  # Bootstrap status (as of May 2026):
  #   ✅ reviewflow-terraform-state-ca   — S3 state bucket (already created)
  #   ✅ reviewflow-terraform-locks      — DynamoDB lock table (already created)
  #   ✅ reviewflow-staging              — EC2 key pair (already created)
  #   ✅ reviewflow-prod                 — EC2 key pair (already created)
  #
  # These were created manually. Do NOT recreate them.
  # Run: terraform init  (first time only — downloads providers, sets backend)
  # ─────────────────────────────────────────────
  backend "s3" {
    bucket         = "reviewflow-terraform-state-ca"
    key            = "reviewflow/terraform.tfstate"
    region         = "ca-central-1"
    encrypt        = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "ReviewFlow"
      Environment = var.environment
      ManagedBy   = "Terraform"
      Owner       = "roqeeb-olamide-ayorinde"
    }
  }
}

# ─────────────────────────────────────────────
# Data sources
# ─────────────────────────────────────────────

# Latest Amazon Linux 2023 AMI — always current
data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# Current AWS account ID — used in IAM policies
data "aws_caller_identity" "current" {}

# ─────────────────────────────────────────────
# Modules
# ─────────────────────────────────────────────

module "iam" {
  source = "./modules/iam"

  environment    = var.environment
  aws_region     = var.aws_region
  account_id     = data.aws_caller_identity.current.account_id
  s3_bucket_name = var.s3_bucket_name
  ecr_repo_name  = var.ecr_repository_name
}

module "s3" {
  source = "./modules/s3"

  environment    = var.environment
  s3_bucket_name = var.s3_bucket_name
  aws_region     = var.aws_region
}

module "ecr" {
  source = "./modules/ecr"

  environment        = var.environment
  repository_name    = var.ecr_repository_name
  image_retain_count = var.ecr_image_retain_count
}

module "security_groups" {
  source = "./modules/security-groups"

  environment = var.environment
  vpc_id      = var.vpc_id
  app_port    = var.app_port
}

module "ec2" {
  source = "./modules/ec2"

  environment          = var.environment
  ami_id               = data.aws_ami.amazon_linux_2023.id
  instance_type        = var.instance_type
  key_name             = var.ec2_key_name
  security_group_ids   = [module.security_groups.app_sg_id]
  iam_instance_profile = module.iam.ec2_instance_profile_name
  app_port             = var.app_port
  aws_region           = var.aws_region
  ecr_repository_url   = module.ecr.repository_url
  s3_bucket_name       = var.s3_bucket_name

  depends_on = [module.iam, module.security_groups]
}

module "cloudwatch" {
  source = "./modules/cloudwatch"

  environment       = var.environment
  aws_region        = var.aws_region
  ec2_instance_id   = module.ec2.instance_id
  app_port          = var.app_port
  alert_email       = var.alert_email
  log_retention_days = var.log_retention_days

  depends_on = [module.ec2]
}
