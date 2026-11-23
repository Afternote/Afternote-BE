resource "aws_s3_bucket" "uploads" {
  bucket = "afternote-storage-079477842373-ap-northeast-2"
}

resource "aws_s3_bucket_public_access_block" "uploads" {
  bucket                  = aws_s3_bucket.uploads.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "uploads" {
  bucket = aws_s3_bucket.uploads.id
  rule { object_ownership = "BucketOwnerEnforced" }
}

resource "aws_cloudfront_origin_access_control" "uploads" {
  name                              = "afternote-uploads"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "uploads" {
  enabled         = true
  is_ipv6_enabled = true
  price_class     = "PriceClass_200"
  origin {
    domain_name              = aws_s3_bucket.uploads.bucket_regional_domain_name
    origin_id                = "uploads"
    origin_access_control_id = aws_cloudfront_origin_access_control.uploads.id
  }
  default_cache_behavior {
    target_origin_id       = "uploads"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    min_ttl                = 0
    default_ttl            = 86400
    max_ttl                = 31536000
    forwarded_values {
      query_string = false
      cookies { forward = "none" }
    }
  }
  restrictions {
    geo_restriction { restriction_type = "none" }
  }
  viewer_certificate { cloudfront_default_certificate = true }
}

resource "aws_s3_bucket_policy" "uploads" {
  bucket = aws_s3_bucket.uploads.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.uploads.arn}/*"
      Condition = { StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.uploads.arn } }
    }]
  })
}

# The current application uses static credentials; keep deployment credentials
# separate from the Terraform operator and limit access to this bucket.
resource "aws_iam_user" "uploads" {
  name = "afternote-app-uploads"
}

resource "aws_iam_user_policy" "uploads" {
  name = "afternote-app-uploads"
  user = aws_iam_user.uploads.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["s3:ListBucket"], Resource = aws_s3_bucket.uploads.arn },
      { Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"], Resource = "${aws_s3_bucket.uploads.arn}/*" }
    ]
  })
}

resource "aws_iam_access_key" "uploads" {
  user = aws_iam_user.uploads.name
}

resource "aws_key_pair" "deploy" {
  key_name   = var.ec2_key_name
  public_key = file("${path.module}/deploy-key.pub")
}

output "uploads_bucket" { value = aws_s3_bucket.uploads.id }
output "uploads_base_url" { value = "https://${aws_cloudfront_distribution.uploads.domain_name}" }
output "uploads_access_key" {
  value     = aws_iam_access_key.uploads.id
  sensitive = true
}
output "uploads_secret_key" {
  value     = aws_iam_access_key.uploads.secret
  sensitive = true
}
