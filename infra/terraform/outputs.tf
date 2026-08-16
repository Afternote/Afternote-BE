output "elastic_ip" {
  description = "Fixed public IP — set domain A record and GitHub secret EC2_HOST"
  value       = data.aws_eip.associated.public_ip
}

output "elastic_ip_allocation_id" {
  description = "Elastic IP allocation ID"
  value       = local.eip_allocation_id
}

output "ec2_instance_id" {
  value = aws_instance.app.id
}

output "rds_endpoint" {
  description = "RDS hostname:port for DB_URL"
  value       = aws_db_instance.main.endpoint
}

output "rds_address" {
  value = aws_db_instance.main.address
}

output "db_name" {
  value = aws_db_instance.main.db_name
}

output "db_username" {
  value = aws_db_instance.main.username
}

output "jdbc_url_hint" {
  description = "Paste into .env.production as DB_URL (password from tfvars)"
  value       = "jdbc:mysql://${aws_db_instance.main.address}:3306/${aws_db_instance.main.db_name}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
}

output "stop_lambda_function_name" {
  value = aws_lambda_function.stop_env.function_name
}

output "start_lambda_function_name" {
  value = aws_lambda_function.start_env.function_name
}

output "stop_schedule" {
  value = "${var.stop_schedule} (${var.schedule_timezone})"
}

output "start_schedule" {
  value = "${var.start_schedule} (${var.schedule_timezone})"
}

output "cloudwatch_dashboard_name" {
  description = "CloudWatch dashboard (EC2 + RDS + scheduler Lambda)"
  value       = aws_cloudwatch_dashboard.scheduler.dashboard_name
}

output "cloudwatch_dashboard_url" {
  description = "Open ops dashboard in console"
  value       = "https://${var.aws_region}.console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.scheduler.dashboard_name}"
}

output "sns_topic_arn" {
  value = aws_sns_topic.scheduler_alerts.arn
}

output "manual_test_commands" {
  description = "Run after apply to verify stop/start without waiting for schedule"
  value       = <<-EOT
    aws lambda invoke --function-name ${aws_lambda_function.stop_env.function_name} /tmp/afternote-stop.json
    aws lambda invoke --function-name ${aws_lambda_function.start_env.function_name} /tmp/afternote-start.json
  EOT
}
