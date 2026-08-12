resource "aws_eip" "app" {
  count  = var.existing_eip_allocation_id == "" ? 1 : 0
  domain = "vpc"

  tags = {
    Name = "${var.project_name}-ec2-eip"
  }
}

locals {
  eip_allocation_id = var.existing_eip_allocation_id != "" ? var.existing_eip_allocation_id : aws_eip.app[0].id
  ec2_instance_id   = aws_instance.app.id
  rds_instance_id   = aws_db_instance.main.identifier
}

resource "aws_eip_association" "app" {
  instance_id   = aws_instance.app.id
  allocation_id = local.eip_allocation_id
}

data "aws_eip" "associated" {
  id = local.eip_allocation_id
}
