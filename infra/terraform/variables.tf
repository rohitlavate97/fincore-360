variable "aws_region" {
  type        = string
  description = "Primary AWS region for FinCore 360 cloud infrastructure"
  default     = "us-east-1"
}

variable "environment" {
  type        = string
  description = "Target deployment environment (staging / production)"
  default     = "production"
}

variable "vpc_cidr" {
  type        = string
  description = "CIDR block for the dedicated banking VPC"
  default     = "10.100.0.0/16"
}

variable "availability_zones" {
  type        = list(string)
  description = "Multi-AZ availability zones for HA fault tolerance"
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "db_instance_class" {
  type        = string
  description = "RDS instance size for PostgreSQL database"
  default     = "db.r6g.xlarge"
}

variable "db_name" {
  type        = string
  description = "Primary PostgreSQL database name"
  default     = "fincore"
}

variable "db_username" {
  type        = string
  description = "Master administrative user name for RDS"
  default     = "fincore_admin"
}

variable "eks_cluster_version" {
  type        = string
  description = "Kubernetes control plane version"
  default     = "1.31"
}
