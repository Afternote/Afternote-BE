variable "project_name" {
  description = "Resource name prefix"
  type        = string
  default     = "afternote"
}

variable "environment" {
  description = "Environment tag (dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "ec2_instance_id" {
  description = "EC2 instance ID to schedule (e.g. i-0abc123)"
  type        = string
}

variable "rds_instance_id" {
  description = "RDS DB instance identifier (e.g. afternote-dev)"
  type        = string
}

variable "existing_eip_allocation_id" {
  description = "Optional existing Elastic IP allocation ID (eipalloc-xxx). Leave empty to create a new EIP."
  type        = string
  default     = ""
}

variable "stop_schedule" {
  description = "EventBridge cron for stop (timezone applied separately)"
  type        = string
  default     = "cron(0 3 * * ? *)"
}

variable "start_schedule" {
  description = "EventBridge cron for start (timezone applied separately)"
  type        = string
  default     = "cron(0 12 * * ? *)"
}

variable "schedule_timezone" {
  description = "IANA timezone for schedules"
  type        = string
  default     = "Asia/Seoul"
}

variable "deploy_path" {
  description = "Absolute path to docker compose stack on EC2"
  type        = string
  default     = "/home/ec2-user/deploy"
}

variable "ec2_user" {
  description = "Linux user that runs docker compose (same as GitHub deploy SSH user)"
  type        = string
  default     = "ec2-user"
}

variable "alert_email" {
  description = "Email for SNS alerts on Lambda failures"
  type        = string
}

variable "create_ec2_ssm_role" {
  description = "Create IAM instance profile for SSM (attach to EC2 manually or via CLI once)"
  type        = bool
  default     = true
}
