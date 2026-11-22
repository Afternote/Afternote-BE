provider "aws" {
  region              = var.aws_region
  profile             = "default"
  allowed_account_ids = ["312871631529"]

  default_tags {
    tags = {
      Project     = var.project_name
      ManagedBy   = "terraform"
      Environment = var.environment
    }
  }
}
