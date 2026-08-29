# FinCore 360 — Cloud Infrastructure as Code (AWS Production Architecture)
# Compliance: PCI-DSS, SOC 2, Multi-AZ High Availability

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project     = "FinCore-360"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# ── 1. NETWORKING (VPC & SUBNETS) ───────────────────────────────
resource "aws_vpc" "fincore_vpc" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "fincore-${var.environment}-vpc"
  }
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.fincore_vpc.id
  tags = {
    Name = "fincore-${var.environment}-igw"
  }
}

# Public Subnets (for NAT Gateways and ALBs)
resource "aws_subnet" "public" {
  count                   = length(var.availability_zones)
  vpc_id                  = aws_vpc.fincore_vpc.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 4, count.index)
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name                     = "fincore-public-${var.availability_zones[count.index]}"
    "kubernetes.io/role/elb" = "1"
  }
}

# Private Subnets (for EKS Worker Nodes and Workloads)
resource "aws_subnet" "private_app" {
  count             = length(var.availability_zones)
  vpc_id            = aws_vpc.fincore_vpc.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index + 4)
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name                              = "fincore-private-app-${var.availability_zones[count.index]}"
    "kubernetes.io/role/internal-elb" = "1"
  }
}

# Isolated Private Subnets (for RDS PostgreSQL)
resource "aws_subnet" "private_db" {
  count             = length(var.availability_zones)
  vpc_id            = aws_vpc.fincore_vpc.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index + 8)
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name = "fincore-private-db-${var.availability_zones[count.index]}"
  }
}

# Elastic IP and NAT Gateway for outbound traffic from private subnets
resource "aws_eip" "nat" {
  domain = "vpc"
  tags = {
    Name = "fincore-nat-eip"
  }
}

resource "aws_nat_gateway" "nat" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id

  tags = {
    Name = "fincore-nat-gw"
  }
  depends_on = [aws_internet_gateway.igw]
}

# Route Tables
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.fincore_vpc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }

  tags = {
    Name = "fincore-public-rt"
  }
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.fincore_vpc.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.nat.id
  }

  tags = {
    Name = "fincore-private-rt"
  }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private_app)
  subnet_id      = aws_subnet.private_app[count.index].id
  route_table_id = aws_route_table.private.id
}

# ── 2. SECURITY GROUPS ───────────────────────────────────────────
resource "aws_security_group" "eks_nodes" {
  name        = "fincore-eks-nodes-sg"
  description = "Security group for EKS worker nodes"
  vpc_id      = aws_vpc.fincore_vpc.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "fincore-eks-nodes-sg"
  }
}

resource "aws_security_group" "rds" {
  name        = "fincore-rds-sg"
  description = "Security group for RDS PostgreSQL database"
  vpc_id      = aws_vpc.fincore_vpc.id

  ingress {
    description     = "Allow PostgreSQL access strictly from EKS nodes"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "fincore-rds-sg"
  }
}

# ── 3. MANAGED EKS CLUSTER ───────────────────────────────────────
resource "aws_iam_role" "eks_cluster" {
  name = "fincore-eks-cluster-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks_cluster.name
}

resource "aws_eks_cluster" "fincore" {
  name     = "fincore-${var.environment}-cluster"
  role_arn = aws_iam_role.eks_cluster.arn
  version  = var.eks_cluster_version

  vpc_config {
    subnet_ids              = aws_subnet.private_app[*].id
    endpoint_private_access = true
    endpoint_public_access  = false
    security_group_ids      = [aws_security_group.eks_nodes.id]
  }

  depends_on = [aws_iam_role_policy_attachment.eks_cluster_policy]
}

resource "aws_iam_role" "eks_nodes" {
  name = "fincore-eks-node-group-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "eks_worker_node" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.eks_nodes.name
}

resource "aws_iam_role_policy_attachment" "eks_cni_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.eks_nodes.name
}

resource "aws_iam_role_policy_attachment" "eks_container_registry" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.eks_nodes.name
}

resource "aws_eks_node_group" "fincore_nodes" {
  cluster_name    = aws_eks_cluster.fincore.name
  node_group_name = "fincore-managed-nodes"
  node_role_arn   = aws_iam_role.eks_nodes.arn
  subnet_ids      = aws_subnet.private_app[*].id

  scaling_config {
    desired_size = 3
    max_size     = 6
    min_size     = 3
  }

  instance_types = ["m6i.xlarge"]
  capacity_type  = "ON_DEMAND"

  labels = {
    role = "fincore-worker"
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.eks_container_registry
  ]
}

# ── 4. MULTI-AZ RDS POSTGRESQL ───────────────────────────────────
resource "aws_db_subnet_group" "rds" {
  name       = "fincore-${var.environment}-db-subnet-group"
  subnet_ids = aws_subnet.private_db[*].id

  tags = {
    Name = "fincore-db-subnet-group"
  }
}

resource "aws_kms_key" "db" {
  description             = "KMS CMK for RDS database encryption"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = {
    Name = "fincore-db-kms-key"
  }
}

resource "random_password" "db_password" {
  length  = 24
  special = false
}

resource "aws_db_instance" "postgresql" {
  identifier        = "fincore-${var.environment}-db"
  allocated_storage = 100
  max_allocated_storage = 500
  storage_type      = "gp3"
  engine            = "postgres"
  engine_version    = "16.3" # Pinned RDS PostgreSQL engine
  instance_class    = var.db_instance_class
  db_name           = var.db_name
  username          = var.db_username
  password          = random_password.db_password.result

  multi_az               = true # High availability multi-AZ failover
  db_subnet_group_name   = aws_db_subnet_group.rds.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  storage_encrypted = true
  kms_key_id        = aws_kms_key.db.arn

  backup_retention_period   = 35 # Regulatory 35-day backup window
  backup_window             = "03:00-04:00"
  maintenance_window        = "Sun:04:30-Sun:05:30"
  auto_minor_version_upgrade = false
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "fincore-${var.environment}-final-snapshot"

  tags = {
    Name = "fincore-primary-database"
  }
}

# ── 5. IMMUTABLE S3 AUDIT ARCHIVE ────────────────────────────────
resource "aws_kms_key" "s3_audit" {
  description             = "KMS CMK for regulatory audit log archive S3 bucket"
  deletion_window_in_days = 30
  enable_key_rotation     = true
}

resource "aws_s3_bucket" "audit_archive" {
  bucket        = "fincore-${var.environment}-audit-archive"
  force_destroy = false

  tags = {
    Name = "fincore-audit-archive"
  }
}

resource "aws_s3_bucket_versioning" "audit_versioning" {
  bucket = aws_s3_bucket.audit_archive.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "audit_crypto" {
  bucket = aws_s3_bucket.audit_archive.id

  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.s3_audit.arn
      sse_algorithm     = "aws:kms"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "audit_block_public" {
  bucket = aws_s3_bucket.audit_archive.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
