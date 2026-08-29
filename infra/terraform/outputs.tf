output "vpc_id" {
  description = "The ID of the banking VPC"
  value       = aws_vpc.fincore_vpc.id
}

output "eks_cluster_endpoint" {
  description = "Endpoint for EKS control plane"
  value       = aws_eks_cluster.fincore.endpoint
}

output "eks_cluster_name" {
  description = "Kubernetes cluster name"
  value       = aws_eks_cluster.fincore.name
}

output "rds_endpoint" {
  description = "Connection endpoint for Multi-AZ PostgreSQL instance"
  value       = aws_db_instance.postgresql.endpoint
}

output "audit_bucket_arn" {
  description = "ARN of the encrypted immutable S3 audit log bucket"
  value       = aws_s3_bucket.audit_archive.arn
}
