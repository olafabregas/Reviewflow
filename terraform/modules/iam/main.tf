# ─────────────────────────────────────────────
# Module: IAM
# Creates:
#   1. EC2 instance role (S3 + CloudWatch + ECR pull)
#   2. CI/CD IAM user (ECR push/pull only)
#   3. All policies — principle of least privilege
# ─────────────────────────────────────────────

# ── EC2 Instance Role ────────────────────────

resource "aws_iam_role" "ec2_role" {
  name = "ReviewFlowEC2Role-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })

  tags = {
    Name = "ReviewFlowEC2Role-${var.environment}"
  }
}

# EC2 → S3 (reviewflow-storage only)
resource "aws_iam_policy" "ec2_s3" {
  name        = "ReviewFlowEC2S3Policy-${var.environment}"
  description = "Allows EC2 to read/write ReviewFlow S3 bucket"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
          "s3:ListBucket",
          "s3:GetObjectVersion",
          "s3:PutObjectAcl"
        ]
        Resource = [
          "arn:aws:s3:::${var.s3_bucket_name}",
          "arn:aws:s3:::${var.s3_bucket_name}/*"
        ]
      }
    ]
  })
}

# EC2 → CloudWatch (logs + metrics)
resource "aws_iam_policy" "ec2_cloudwatch" {
  name        = "ReviewFlowEC2CloudWatchPolicy-${var.environment}"
  description = "Allows EC2 CloudWatch agent to ship logs and metrics"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "cloudwatch:PutMetricData",
          "cloudwatch:GetMetricData",
          "cloudwatch:ListMetrics",
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams",
          "logs:DescribeLogGroups"
        ]
        Resource = "*"
      }
    ]
  })
}

# EC2 → ECR (pull images only — not push)
resource "aws_iam_policy" "ec2_ecr" {
  name        = "ReviewFlowEC2ECRPolicy-${var.environment}"
  description = "Allows EC2 to pull images from ECR"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken",
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage"
        ]
        Resource = "*"
      }
    ]
  })
}

# Attach all policies to EC2 role
resource "aws_iam_role_policy_attachment" "ec2_s3" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = aws_iam_policy.ec2_s3.arn
}

resource "aws_iam_role_policy_attachment" "ec2_cloudwatch" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = aws_iam_policy.ec2_cloudwatch.arn
}

resource "aws_iam_role_policy_attachment" "ec2_ecr" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = aws_iam_policy.ec2_ecr.arn
}

# Instance profile — what actually attaches to EC2
resource "aws_iam_instance_profile" "ec2_profile" {
  name = "ReviewFlowEC2Profile-${var.environment}"
  role = aws_iam_role.ec2_role.name
}

# ── CI/CD IAM User ────────────────────────────
# Used by GitHub Actions for ECR push/pull + image cleanup.
# No console access. Access keys only.
# Scoped to ECR only — EC2 deploys via SSH, not IAM.

resource "aws_iam_user" "ci_user" {
  name = "reviewflow-ci-user"
  path = "/ci/"

  tags = {
    Purpose = "GitHub Actions CI/CD - ECR access only"
  }
}

resource "aws_iam_policy" "ci_ecr" {
  name        = "ReviewFlowCIECRPolicy"
  description = "GitHub Actions CI/CD - ECR push/pull and image cleanup"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken",
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:DescribeImages",
          "ecr:BatchDeleteImage"
        ]
        Resource = "arn:aws:ecr:${var.aws_region}:${var.account_id}:repository/${var.ecr_repo_name}"
      },
      {
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_user_policy_attachment" "ci_ecr" {
  user       = aws_iam_user.ci_user.name
  policy_arn = aws_iam_policy.ci_ecr.arn
}

# Access keys for CI user — output to be added to GitHub Secrets
# NOTE: Terraform stores these in state. State is encrypted in S3.
resource "aws_iam_access_key" "ci_user" {
  user = aws_iam_user.ci_user.name
}
