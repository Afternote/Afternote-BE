terraform {
  backend "s3" {
    bucket              = "afternote-terraform-state-079477842373-ap-northeast-2"
    key                 = "prod/terraform.tfstate"
    region              = "ap-northeast-2"
    profile             = "afternote-pm"
    allowed_account_ids = ["079477842373"]
    encrypt             = true
    use_lockfile        = true
  }
}
