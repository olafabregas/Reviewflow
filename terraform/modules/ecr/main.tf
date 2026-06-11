# ─────────────────────────────────────────────
# Module: ECR
# Single repository for ReviewFlow Docker images.
# Images tagged:
#   staging-{sha}     on every merge to main
#   prod-{date}-{sha} on production deploy
#   staging-latest    always points to newest staging
#   prod-latest       always points to newest prod
# ─────────────────────────────────────────────

resource "aws_ecr_repository" "reviewflow" {
  name                 = var.repository_name
  image_tag_mutability = "MUTABLE"  # Required for 'latest' tag updates

  # Scan images on push — catches OS-level CVEs in base image
  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name = var.repository_name
  }
}

# Lifecycle policy — keep last N images per tag prefix
# Prevents unbounded storage growth
resource "aws_ecr_lifecycle_policy" "reviewflow" {
  repository = aws_ecr_repository.reviewflow.name

  policy = jsonencode({
    rules = [
      {
        # Keep last 10 staging images
        rulePriority = 1
        description  = "Keep last ${var.image_retain_count} staging images"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["staging-"]
          countType     = "imageCountMoreThan"
          countNumber   = var.image_retain_count
        }
        action = { type = "expire" }
      },
      {
        # Keep all prod images — never auto-expire production
        # Manual cleanup only via nightly.yml ECR cleanup job
        rulePriority = 2
        description  = "Keep all prod images"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["prod-"]
          countType     = "imageCountMoreThan"
          countNumber   = 999
        }
        action = { type = "expire" }
      },
      {
        # Clean up untagged images after 1 day
        rulePriority = 3
        description  = "Remove untagged images after 1 day"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      }
    ]
  })
}
