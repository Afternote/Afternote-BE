data "archive_file" "stop_lambda" {
  type        = "zip"
  source_file = "${path.module}/lambda_stop.py"
  output_path = "${path.module}/.build/stop_lambda.zip"
}

data "archive_file" "start_lambda" {
  type        = "zip"
  source_file = "${path.module}/lambda_start.py"
  output_path = "${path.module}/.build/start_lambda.zip"
}

resource "aws_cloudwatch_log_group" "stop_lambda" {
  name              = "/aws/lambda/${var.project_name}-stop-env"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "start_lambda" {
  name              = "/aws/lambda/${var.project_name}-start-env"
  retention_in_days = 14
}

resource "aws_lambda_function" "stop_env" {
  function_name = "${var.project_name}-stop-env"
  role          = aws_iam_role.lambda_scheduler.arn
  handler       = "lambda_stop.handler"
  runtime       = "python3.12"
  timeout       = 120
  memory_size   = 128

  filename         = data.archive_file.stop_lambda.output_path
  source_code_hash = data.archive_file.stop_lambda.output_base64sha256

  environment {
    variables = {
      EC2_INSTANCE_ID = var.ec2_instance_id
      RDS_INSTANCE_ID = var.rds_instance_id
    }
  }

  depends_on = [aws_cloudwatch_log_group.stop_lambda]
}

resource "aws_lambda_function" "start_env" {
  function_name = "${var.project_name}-start-env"
  role          = aws_iam_role.lambda_scheduler.arn
  handler       = "lambda_start.handler"
  runtime       = "python3.12"
  timeout       = 600
  memory_size   = 128

  filename         = data.archive_file.start_lambda.output_path
  source_code_hash = data.archive_file.start_lambda.output_base64sha256

  environment {
    variables = {
      EC2_INSTANCE_ID = var.ec2_instance_id
      RDS_INSTANCE_ID = var.rds_instance_id
      DEPLOY_PATH     = var.deploy_path
      EC2_USER        = var.ec2_user
      SNS_TOPIC_ARN   = aws_sns_topic.scheduler_alerts.arn
    }
  }

  depends_on = [aws_cloudwatch_log_group.start_lambda]
}

resource "aws_lambda_permission" "scheduler_stop" {
  statement_id  = "AllowEventBridgeSchedulerStop"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.stop_env.function_name
  principal     = "scheduler.amazonaws.com"
  source_arn    = aws_scheduler_schedule.stop_env.arn
}

resource "aws_lambda_permission" "scheduler_start" {
  statement_id  = "AllowEventBridgeSchedulerStart"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.start_env.function_name
  principal     = "scheduler.amazonaws.com"
  source_arn    = aws_scheduler_schedule.start_env.arn
}
