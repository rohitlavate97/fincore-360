terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Production backend: S3 bucket with DynamoDB state locking
  # backend "s3" {
  #   bucket         = "fincore-terraform-state"
  #   key            = "environments/prod/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "fincore-terraform-locks"
  #   encrypt        = true
  # }
}
