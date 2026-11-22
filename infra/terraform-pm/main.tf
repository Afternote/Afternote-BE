# PM account stack. Keep its local state separate from ../terraform.
# Run Terraform in this directory using the afternote-pm AWS profile.
provider "aws" {
  region              = var.aws_region
  profile             = "afternote-pm"
  allowed_account_ids = ["079477842373"]

  default_tags {
    tags = {
      Project     = var.project_name
      ManagedBy   = "terraform"
      Environment = var.environment
    }
  }
}
