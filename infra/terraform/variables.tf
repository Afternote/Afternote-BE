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

variable "vpc_cidr" {
  description = "CIDR for the greenfield VPC"
  type        = string
  default     = "10.40.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "Two public subnet CIDRs in different AZs (RDS subnet group requires 2 AZs)"
  type        = list(string)
  default     = ["10.40.1.0/24", "10.40.2.0/24"]
}

variable "ssh_ingress_cidr" {
  description = "CIDR allowed to SSH into EC2 (prefer your IP/32)"
  type        = string
}

variable "ec2_key_name" {
  description = "Existing EC2 key pair name for SSH"
  type        = string
}

variable "ec2_instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.micro"
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_name" {
  description = "Initial MySQL database name"
  type        = string
  default     = "afternote"
}

variable "db_username" {
  description = "RDS master username"
  type        = string
  default     = "afternote"
}

variable "db_password" {
  description = "RDS master password (store in terraform.tfvars, never commit)"
  type        = string
  sensitive   = true
}

variable "db_allocated_storage" {
  description = "RDS allocated storage in GB"
  type        = number
  default     = 20
}

variable "existing_eip_allocation_id" {
  description = "Optional existing Elastic IP allocation ID (eipalloc-xxx). Leave empty to create a new EIP."
  type        = string
  default     = ""
}

variable "stop_schedule" {
  description = "EventBridge cron for stop (timezone applied separately)"
  type        = string
  default     = "cron(0 1 * * ? *)"
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
