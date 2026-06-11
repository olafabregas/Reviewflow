# ─────────────────────────────────────────────
# Module: CloudWatch
# Creates:
#   - Log groups (application + security)
#   - SNS topic for alerts
#   - 10 CloudWatch Alarms (free tier limit)
#   - CloudWatch Dashboard
# ─────────────────────────────────────────────

# ── Log Groups ────────────────────────────────

resource "aws_cloudwatch_log_group" "application" {
  name              = "/reviewflow/application"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "reviewflow-application-logs"
  }
}

resource "aws_cloudwatch_log_group" "security" {
  name              = "/reviewflow/security"
  retention_in_days = 90  # Security logs kept longer regardless of env setting

  tags = {
    Name = "reviewflow-security-logs"
  }
}

# ── SNS Topic for Alerts ──────────────────────

resource "aws_sns_topic" "alerts" {
  name = "reviewflow-${var.environment}-alerts"

  tags = {
    Name = "reviewflow-${var.environment}-alerts"
  }
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# ── CloudWatch Alarms (10 free tier) ──────────
# Priority order — most critical first.
# All alarm → SNS → email.

# 1. CPU > 80% for 5 minutes
resource "aws_cloudwatch_metric_alarm" "cpu_high" {
  alarm_name          = "reviewflow-${var.environment}-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "EC2 CPU > 80% for 10 minutes"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    InstanceId = var.ec2_instance_id
  }
}

# 2. Memory > 85%
# Requires CloudWatch Agent (installed in user_data)
resource "aws_cloudwatch_metric_alarm" "memory_high" {
  alarm_name          = "reviewflow-${var.environment}-memory-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "mem_used_percent"
  namespace           = "ReviewFlow/${var.environment}"
  period              = 300
  statistic           = "Average"
  threshold           = 85
  alarm_description   = "EC2 memory > 85% — risk of OOM on t3.micro"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# 3. Disk > 80%
resource "aws_cloudwatch_metric_alarm" "disk_high" {
  alarm_name          = "reviewflow-${var.environment}-disk-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "disk_used_percent"
  namespace           = "ReviewFlow/${var.environment}"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "EC2 disk > 80% — Docker images or logs filling up"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

# 4. Instance status check failed
resource "aws_cloudwatch_metric_alarm" "instance_status" {
  alarm_name          = "reviewflow-${var.environment}-instance-status"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "StatusCheckFailed"
  namespace           = "AWS/EC2"
  period              = 60
  statistic           = "Maximum"
  threshold           = 0
  alarm_description   = "EC2 instance status check failed — hardware or OS issue"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    InstanceId = var.ec2_instance_id
  }
}

# 5. ERROR log rate > 10 per minute
resource "aws_cloudwatch_metric_alarm" "error_rate" {
  alarm_name          = "reviewflow-${var.environment}-error-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "ErrorCount"
  namespace           = "ReviewFlow/${var.environment}"
  period              = 60
  statistic           = "Sum"
  threshold           = 10
  treat_missing_data  = "notBreaching"
  alarm_description   = "Application ERROR log rate > 10/min — check application logs"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

# Log metric filter — counts ERROR lines in application log
resource "aws_cloudwatch_log_metric_filter" "error_count" {
  name           = "reviewflow-${var.environment}-error-count"
  pattern        = "[timestamp, level=ERROR, ...]"
  log_group_name = aws_cloudwatch_log_group.application.name

  metric_transformation {
    name          = "ErrorCount"
    namespace     = "ReviewFlow/${var.environment}"
    value         = "1"
    default_value = "0"
  }
}

# 6. Auth failure rate > 20 per minute (brute force indicator)
resource "aws_cloudwatch_metric_alarm" "auth_failures" {
  alarm_name          = "reviewflow-${var.environment}-auth-failures"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "AuthFailureCount"
  namespace           = "ReviewFlow/${var.environment}"
  period              = 60
  statistic           = "Sum"
  threshold           = 20
  treat_missing_data  = "notBreaching"
  alarm_description   = "Auth failure rate > 20/min — possible brute force attack"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_log_metric_filter" "auth_failures" {
  name           = "reviewflow-${var.environment}-auth-failure-filter"
  pattern        = "[timestamp, level, logger, message=\"*authentication failed*\", ...]"
  log_group_name = aws_cloudwatch_log_group.security.name

  metric_transformation {
    name          = "AuthFailureCount"
    namespace     = "ReviewFlow/${var.environment}"
    value         = "1"
    default_value = "0"
  }
}

# 7. Rate limiter triggered > 50 per minute
resource "aws_cloudwatch_metric_alarm" "rate_limit_hits" {
  alarm_name          = "reviewflow-${var.environment}-rate-limit"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "RateLimitHits"
  namespace           = "ReviewFlow/${var.environment}"
  period              = 60
  statistic           = "Sum"
  threshold           = 50
  treat_missing_data  = "notBreaching"
  alarm_description   = "Rate limiter triggered > 50/min — possible abuse"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_log_metric_filter" "rate_limit" {
  name           = "reviewflow-${var.environment}-rate-limit-filter"
  pattern        = "[timestamp, level, logger, message=\"*rate limit exceeded*\", ...]"
  log_group_name = aws_cloudwatch_log_group.security.name

  metric_transformation {
    name          = "RateLimitHits"
    namespace     = "ReviewFlow/${var.environment}"
    value         = "1"
    default_value = "0"
  }
}

# 8. Blocked file upload attempts
resource "aws_cloudwatch_metric_alarm" "blocked_uploads" {
  alarm_name          = "reviewflow-${var.environment}-blocked-uploads"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "BlockedUploadCount"
  namespace           = "ReviewFlow/${var.environment}"
  period              = 300
  statistic           = "Sum"
  threshold           = 5
  treat_missing_data  = "notBreaching"
  alarm_description   = "FileSecurityValidator blocked > 5 uploads in 5 min — possible malicious upload attempt"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_log_metric_filter" "blocked_uploads" {
  name           = "reviewflow-${var.environment}-blocked-upload-filter"
  pattern        = "[timestamp, level, logger, message=\"*file validation failed*\", ...]"
  log_group_name = aws_cloudwatch_log_group.security.name

  metric_transformation {
    name          = "BlockedUploadCount"
    namespace     = "ReviewFlow/${var.environment}"
    value         = "1"
    default_value = "0"
  }
}

# 9. 5xx response rate > 5 per minute
resource "aws_cloudwatch_metric_alarm" "http_5xx" {
  alarm_name          = "reviewflow-${var.environment}-5xx-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "Http5xxCount"
  namespace           = "ReviewFlow/${var.environment}"
  period              = 60
  statistic           = "Sum"
  threshold           = 5
  treat_missing_data  = "notBreaching"
  alarm_description   = "5xx error rate > 5/min — application errors"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_log_metric_filter" "http_5xx" {
  name           = "reviewflow-${var.environment}-5xx-filter"
  pattern        = "[timestamp, level=ERROR, logger=\"*Controller*\", ...]"
  log_group_name = aws_cloudwatch_log_group.application.name

  metric_transformation {
    name          = "Http5xxCount"
    namespace     = "ReviewFlow/${var.environment}"
    value         = "1"
    default_value = "0"
  }
}

# 10. Health check endpoint down
# Polls /actuator/health via synthetic monitor
# Uses CloudWatch Synthetics canary (replaces UptimeRobot for AWS-native monitoring)
resource "aws_cloudwatch_metric_alarm" "health_check" {
  alarm_name          = "reviewflow-${var.environment}-health-check"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 3
  metric_name         = "SuccessPercent"
  namespace           = "CloudWatchSynthetics"
  period              = 60
  statistic           = "Average"
  threshold           = 100
  treat_missing_data  = "breaching"
  alarm_description   = "Health check canary failing — app may be down"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]

  dimensions = {
    CanaryName = "reviewflow-${var.environment}-health"
  }
}

# ── CloudWatch Dashboard ──────────────────────

resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "ReviewFlow-${var.environment}"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title   = "CPU Utilization"
          region  = var.aws_region
          period  = 300
          stat    = "Average"
          view    = "timeSeries"
          metrics = [["AWS/EC2", "CPUUtilization", "InstanceId", var.ec2_instance_id]]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title   = "Memory Used %"
          region  = var.aws_region
          period  = 300
          stat    = "Average"
          view    = "timeSeries"
          metrics = [["ReviewFlow/${var.environment}", "mem_used_percent"]]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          title   = "Error Rate"
          region  = var.aws_region
          period  = 60
          stat    = "Sum"
          view    = "timeSeries"
          metrics = [["ReviewFlow/${var.environment}", "ErrorCount"]]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 6
        width  = 12
        height = 6
        properties = {
          title   = "Auth Failures"
          region  = var.aws_region
          period  = 60
          stat    = "Sum"
          view    = "timeSeries"
          metrics = [["ReviewFlow/${var.environment}", "AuthFailureCount"]]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 12
        height = 6
        properties = {
          title   = "Blocked Upload Attempts"
          region  = var.aws_region
          period  = 300
          stat    = "Sum"
          view    = "timeSeries"
          metrics = [["ReviewFlow/${var.environment}", "BlockedUploadCount"]]
        }
      },
      {
        type   = "log"
        x      = 12
        y      = 12
        width  = 12
        height = 6
        properties = {
          title  = "Recent Errors"
          region = var.aws_region
          view   = "table"
          query  = "SOURCE '/reviewflow/application' | fields @timestamp, @message | filter @message like /ERROR/ | sort @timestamp desc | limit 20"
        }
      }
    ]
  })
}