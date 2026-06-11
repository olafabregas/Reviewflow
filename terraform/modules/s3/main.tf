# ─────────────────────────────────────────────
# Module: S3
# ReviewFlow file storage bucket.
# Key structure (enforced by application, not S3):
#   submissions/{hashedAssignmentId}/{hashedOwnerId}/v{n}/{filename}
#   pdfs/{hashedEvaluationId}/report.pdf
#   avatars/{hashedUserId}/avatar.{ext}
#   materials/{hashedCourseId}/{hashedMaterialId}/{filename}
#   messages/{hashedConversationId}/{hashedMessageId}/{filename}
# ─────────────────────────────────────────────

resource "aws_s3_bucket" "reviewflow" {
  bucket = var.s3_bucket_name

  # Prevent accidental destruction of a bucket with student data
  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name = var.s3_bucket_name
  }
}

# Block all public access — files served via pre-signed URLs only
resource "aws_s3_bucket_public_access_block" "reviewflow" {
  bucket = aws_s3_bucket.reviewflow.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Versioning — protects against accidental overwrites
# Submissions are versioned at the application layer (v1, v2, v3 in key)
# S3 versioning is an additional safety net
resource "aws_s3_bucket_versioning" "reviewflow" {
  bucket = aws_s3_bucket.reviewflow.id

  versioning_configuration {
    status = "Enabled"
  }
}

# Server-side encryption — AES-256
resource "aws_s3_bucket_server_side_encryption_configuration" "reviewflow" {
  bucket = aws_s3_bucket.reviewflow.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# CORS — required for pre-signed URL direct uploads from browser
# When React frontend is built, uploads will go directly to S3
# via pre-signed URLs, not through the Spring Boot backend
resource "aws_s3_bucket_cors_configuration" "reviewflow" {
  bucket = aws_s3_bucket.reviewflow.id

  cors_rule {
    allowed_headers = ["Content-Type", "Content-Disposition", "Authorization"]
    allowed_methods = ["GET", "PUT", "POST"]
    allowed_origins = [
      "https://reviewflowlms.com",
      "https://staging.reviewflowlms.com",
      "http://localhost:3000",  # React dev server
      "http://localhost:5173"   # Vite dev server
    ]
    expose_headers  = ["ETag"]
    max_age_seconds = 3600
  }
}

# Lifecycle rules — manage storage costs
resource "aws_s3_bucket_lifecycle_configuration" "reviewflow" {
  bucket = aws_s3_bucket.reviewflow.id

  # Move old submission versions to cheaper storage after 90 days
  rule {
    id     = "submission-noncurrent-versions"
    status = "Enabled"

    filter {
      prefix = "submissions/"
    }

    noncurrent_version_transition {
      noncurrent_days = 90
      storage_class   = "STANDARD_IA"
    }

    noncurrent_version_expiration {
      noncurrent_days = 365
    }
  }

  # Clean up incomplete multipart uploads after 7 days
  rule {
    id     = "abort-incomplete-multipart"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  # PDF reports: move to IA after 180 days
  rule {
    id     = "pdf-reports-lifecycle"
    status = "Enabled"

    filter {
      prefix = "pdfs/"
    }

    transition {
      days          = 180
      storage_class = "STANDARD_IA"
    }
  }
}
