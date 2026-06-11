# ─────────────────────────────────────────────
# Module: EC2
# Creates the EC2 instance with:
#   - IAM instance profile attached
#   - Security group applied
#   - User data script for first-boot setup
#   - Docker + Docker Compose installed
#   - CloudWatch agent installed
# ─────────────────────────────────────────────

resource "aws_instance" "app" {
  ami                    = var.ami_id
  instance_type          = var.instance_type
  key_name               = var.key_name
  vpc_security_group_ids = var.security_group_ids
  iam_instance_profile   = var.iam_instance_profile

  # Root volume — 20GB is enough for OS + Docker images + logs
  root_block_device {
    volume_type           = "gp3"
    volume_size           = 30
    delete_on_termination = true
    encrypted             = true

    tags = {
      Name = "reviewflow-${var.environment}-root"
    }
  }

  # User data — runs once on first boot
  # Installs Docker, Docker Compose, CloudWatch Agent
  # Creates app directory structure
  user_data = base64encode(templatefile("${path.module}/templates/user_data.sh.tpl", {
    environment        = var.environment
    app_port           = var.app_port
    aws_region         = var.aws_region
    ecr_repository_url = var.ecr_repository_url
    s3_bucket_name     = var.s3_bucket_name
  }))

  # Prevent accidental termination of production instance
  disable_api_termination = var.environment == "prod" ? true : false

  tags = {
    Name = "reviewflow-${var.environment}"
  }

  lifecycle {
    # Never replace the instance if only the AMI changes
    # (would destroy the running app)
    # To update AMI: drain → terminate → apply
    ignore_changes = [ami, user_data]
  }
}

# Elastic IP — keeps the IP stable across stop/start
# Required so Cloudflare DNS doesn't need updating after EC2 restarts
resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"

  tags = {
    Name = "reviewflow-${var.environment}-eip"
  }
}
