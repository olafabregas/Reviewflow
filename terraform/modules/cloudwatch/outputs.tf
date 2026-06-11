output "log_group_app" { value = aws_cloudwatch_log_group.application.name }
output "log_group_security" { value = aws_cloudwatch_log_group.security.name }
output "sns_topic_arn" { value = aws_sns_topic.alerts.arn }
output "dashboard_name" { value = aws_cloudwatch_dashboard.main.dashboard_name }
