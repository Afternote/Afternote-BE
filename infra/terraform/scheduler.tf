resource "aws_scheduler_schedule" "stop_env" {
  name       = "${var.project_name}-stop-env"
  group_name = "default"

  schedule_expression          = var.stop_schedule
  schedule_expression_timezone = var.schedule_timezone
  state                        = "ENABLED"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_lambda_function.stop_env.arn
    role_arn = aws_iam_role.scheduler_invoke.arn

    retry_policy {
      maximum_retry_attempts = 2
    }
  }
}

resource "aws_scheduler_schedule" "start_env" {
  name       = "${var.project_name}-start-env"
  group_name = "default"

  schedule_expression          = var.start_schedule
  schedule_expression_timezone = var.schedule_timezone
  state                        = "ENABLED"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_lambda_function.start_env.arn
    role_arn = aws_iam_role.scheduler_invoke.arn

    retry_policy {
      maximum_retry_attempts = 2
    }
  }
}
