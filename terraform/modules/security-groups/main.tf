# ─────────────────────────────────────────────
# Module: Security Groups
# Principle of least privilege — only open
# what ReviewFlow actually needs.
# ─────────────────────────────────────────────

# Look up default VPC if none specified
data "aws_vpc" "selected" {
  id      = var.vpc_id != "" ? var.vpc_id : null
  default = var.vpc_id == "" ? true : null
}

resource "aws_security_group" "app" {
  name        = "reviewflow-${var.environment}-app"
  description = "ReviewFlow ${var.environment} application security group"
  vpc_id      = data.aws_vpc.selected.id

  # ── Inbound ──────────────────────────────

  # HTTPS — public traffic
  ingress {
    description = "HTTPS from internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # HTTP — redirect to HTTPS (Cloudflare handles this)
  ingress {
    description = "HTTP from internet (Cloudflare redirect)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # App port — Cloudflare proxies to this port
  # Cloudflare IP ranges only in production would be ideal,
  # but 0.0.0.0/0 is acceptable while Cloudflare proxying is enabled
  ingress {
    description = "Spring Boot application"
    from_port   = var.app_port
    to_port     = var.app_port
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # SSH — restricted to your IP only
  # IMPORTANT: Replace 0.0.0.0/0 with your actual IP before apply
  # Use: curl ifconfig.me to get your current IP
  # Then set: ssh_cidr = ["YOUR.IP.HERE/32"]
  ingress {
    description = "SSH access"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.ssh_allowed_cidrs
  }

  # ── Outbound ─────────────────────────────

  # All outbound allowed — EC2 needs to reach:
  #   - ECR (image pulls)
  #   - S3 (file storage)
  #   - CloudWatch (logs + metrics)
  #   - Resend SMTP (port 587)
  #   - NVD for OWASP scans (not on EC2 but in CI)
  egress {
    description = "All outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "reviewflow-${var.environment}-app-sg"
  }
}
