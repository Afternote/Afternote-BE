data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_instance" "app" {
  ami                         = data.aws_ami.al2023.id
  instance_type               = var.ec2_instance_type
  subnet_id                   = aws_subnet.public[0].id
  vpc_security_group_ids      = [aws_security_group.ec2.id]
  key_name                    = var.ec2_key_name
  iam_instance_profile        = aws_iam_instance_profile.ec2_ssm.name
  associate_public_ip_address = true
  user_data = templatefile("${path.module}/templates/ec2_user_data.sh.tftpl", {
    deploy_path = var.deploy_path
    ec2_user    = var.ec2_user
  })

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
  }

  tags = {
    Name = "${var.project_name}-app"
  }

  # most_recent AMI가 바뀌어도 운영 중인 인스턴스를 교체하지 않는다.
  lifecycle {
    ignore_changes = [ami]
  }
}
