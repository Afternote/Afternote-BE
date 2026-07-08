output "elastic_ip" {
  description = "Fixed public IP — set domain A record and GitHub secret EC2_HOST once"
  value       = data.aws_eip.associated.public_ip
}

output "elastic_ip_allocation_id" {
  description = "Elastic IP allocation ID"
  value       = local.eip_allocation_id
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
  value = aws_cloudwatch_dashboard.scheduler.dashboard_name
}

output "sns_topic_arn" {
  value = aws_sns_topic.scheduler_alerts.arn
}

output "ec2_ssm_instance_profile_name" {
  description = "Attach this profile to EC2 if SSM is not Online yet (see README)"
  value       = var.create_ec2_ssm_role ? aws_iam_instance_profile.ec2_ssm[0].name : null
}

output "manual_test_commands" {
  description = "Run after apply to verify stop/start without waiting for schedule"
  value       = <<-EOT
    aws lambda invoke --function-name ${aws_lambda_function.stop_env.function_name} /tmp/afternote-stop.json
    aws lambda invoke --function-name ${aws_lambda_function.start_env.function_name} /tmp/afternote-start.json
  EOT
}
