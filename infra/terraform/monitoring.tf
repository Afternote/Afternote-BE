resource "aws_sns_topic" "scheduler_alerts" {
  name = "${var.project_name}-scheduler-alerts"
}

resource "aws_sns_topic_subscription" "scheduler_alerts_email" {
  topic_arn = aws_sns_topic.scheduler_alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

resource "aws_cloudwatch_metric_alarm" "stop_lambda_errors" {
  alarm_name          = "${var.project_name}-stop-lambda-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  alarm_description   = "Stop Lambda failed"

  dimensions = {
    FunctionName = aws_lambda_function.stop_env.function_name
  }

  alarm_actions = [aws_sns_topic.scheduler_alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "start_lambda_errors" {
  alarm_name          = "${var.project_name}-start-lambda-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  alarm_description   = "Start Lambda failed (environment may not be running)"

  dimensions = {
    FunctionName = aws_lambda_function.start_env.function_name
  }

  alarm_actions = [aws_sns_topic.scheduler_alerts.arn]
}

resource "aws_cloudwatch_dashboard" "scheduler" {
  dashboard_name = "${var.project_name}-scheduler"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title  = "Lambda Invocations"
          region = var.aws_region
          metrics = [
            ["AWS/Lambda", "Invocations", "FunctionName", aws_lambda_function.stop_env.function_name, { stat = "Sum", label = "Stop" }],
            [".", "Invocations", ".", aws_lambda_function.start_env.function_name, { stat = "Sum", label = "Start" }],
          ]
          period = 300
          view   = "timeSeries"
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title  = "Lambda Errors"
          region = var.aws_region
          metrics = [
            ["AWS/Lambda", "Errors", "FunctionName", aws_lambda_function.stop_env.function_name, { stat = "Sum", label = "Stop" }],
            [".", "Errors", ".", aws_lambda_function.start_env.function_name, { stat = "Sum", label = "Start" }],
          ]
          period = 300
          view   = "timeSeries"
        }
      },
    ]
  })
}
