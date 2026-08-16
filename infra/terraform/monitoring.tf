# --- App logs (docker awslogs) ---

resource "aws_cloudwatch_log_group" "app" {
  name              = "/${var.project_name}/app"
  retention_in_days = 14

  tags = {
    Name = "${var.project_name}-app-logs"
  }
}

# Infra alerts (EC2 / RDS / scheduler Lambda). Same SNS topic for all.
# Off-hours EC2/RDS stop: missing metrics must NOT alarm → treat_missing_data = notBreaching.

resource "aws_sns_topic" "scheduler_alerts" {
  name = "${var.project_name}-scheduler-alerts"
}

resource "aws_sns_topic_subscription" "scheduler_alerts_email" {
  topic_arn = aws_sns_topic.scheduler_alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# --- Lambda (existing) ---

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

# --- EC2 ---

resource "aws_cloudwatch_metric_alarm" "ec2_status_check" {
  alarm_name          = "${var.project_name}-ec2-status-check"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "StatusCheckFailed"
  namespace           = "AWS/EC2"
  period              = 60
  statistic           = "Maximum"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  alarm_description   = "EC2 instance status check failed (host/instance unhealthy). Silent while stopped off-hours."

  dimensions = {
    InstanceId = aws_instance.app.id
  }

  alarm_actions = [aws_sns_topic.scheduler_alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "ec2_cpu_high" {
  alarm_name          = "${var.project_name}-ec2-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  treat_missing_data  = "notBreaching"
  alarm_description   = "EC2 CPU > 80% for 10 minutes. Silent while stopped off-hours."

  dimensions = {
    InstanceId = aws_instance.app.id
  }

  alarm_actions = [aws_sns_topic.scheduler_alerts.arn]
}

# --- RDS ---

resource "aws_cloudwatch_metric_alarm" "rds_free_storage_low" {
  alarm_name          = "${var.project_name}-rds-free-storage-low"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  # ~2 GiB
  threshold           = 2147483648
  treat_missing_data  = "notBreaching"
  alarm_description   = "RDS free storage < 2 GiB. Silent while stopped off-hours."

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.main.identifier
  }

  alarm_actions = [aws_sns_topic.scheduler_alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds_connections_high" {
  alarm_name          = "${var.project_name}-rds-connections-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 40
  treat_missing_data  = "notBreaching"
  alarm_description   = "RDS connections > 40 for 10 minutes (pool leak / traffic spike). Silent while stopped off-hours."

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.main.identifier
  }

  alarm_actions = [aws_sns_topic.scheduler_alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds_cpu_high" {
  alarm_name          = "${var.project_name}-rds-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  treat_missing_data  = "notBreaching"
  alarm_description   = "RDS CPU > 80% for 10 minutes. Silent while stopped off-hours."

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.main.identifier
  }

  alarm_actions = [aws_sns_topic.scheduler_alerts.arn]
}

# --- Dashboard ---

resource "aws_cloudwatch_dashboard" "scheduler" {
  dashboard_name = "${var.project_name}-ops"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title  = "EC2 CPU %"
          region = var.aws_region
          metrics = [
            ["AWS/EC2", "CPUUtilization", "InstanceId", aws_instance.app.id, { stat = "Average", label = "CPU" }],
          ]
          period = 300
          view   = "timeSeries"
          yAxis = {
            left = { min = 0, max = 100 }
          }
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title  = "EC2 StatusCheckFailed"
          region = var.aws_region
          metrics = [
            ["AWS/EC2", "StatusCheckFailed", "InstanceId", aws_instance.app.id, { stat = "Maximum", label = "Failed" }],
          ]
          period = 60
          view   = "timeSeries"
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 8
        height = 6
        properties = {
          title  = "RDS CPU %"
          region = var.aws_region
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", aws_db_instance.main.identifier, { stat = "Average" }],
          ]
          period = 300
          view   = "timeSeries"
          yAxis = {
            left = { min = 0, max = 100 }
          }
        }
      },
      {
        type   = "metric"
        x      = 8
        y      = 6
        width  = 8
        height = 6
        properties = {
          title  = "RDS FreeStorageSpace"
          region = var.aws_region
          metrics = [
            ["AWS/RDS", "FreeStorageSpace", "DBInstanceIdentifier", aws_db_instance.main.identifier, { stat = "Average" }],
          ]
          period = 300
          view   = "timeSeries"
        }
      },
      {
        type   = "metric"
        x      = 16
        y      = 6
        width  = 8
        height = 6
        properties = {
          title  = "RDS DatabaseConnections"
          region = var.aws_region
          metrics = [
            ["AWS/RDS", "DatabaseConnections", "DBInstanceIdentifier", aws_db_instance.main.identifier, { stat = "Average" }],
          ]
          period = 300
          view   = "timeSeries"
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 12
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
        y      = 12
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
