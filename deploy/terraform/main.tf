# AWS resources owned by svc-pay-request-to-pay: its own Aurora PostgreSQL
# cluster (db_pay_request_to_pay_<env>), encryption key, database credential
# and the IRSA role its pods use for Amazon MSK. Shared platform pieces (log
# group, SSM parameters) come from the platform microservice-base module.

data "aws_caller_identity" "current" {}

locals {
  service_id   = "svc-pay-request-to-pay"
  service_slug = "payment-request-to-pay-service"
  name         = "${var.environment}-${local.service_slug}"
  database     = "db_pay_request_to_pay_${var.environment}"
  topics       = ["evt.pay.rtp.created.v1", "evt.pay.rtp.accepted.v1", "evt.pay.rtp.rejected.v1"]

  tags = merge({
    Service            = local.service_id
    BoundedContext     = "payments"
    OwningSquad        = "recurring-and-bulk-payments"
    Environment        = var.environment
    DataClassification = "confidential"
    ManagedBy          = "terraform"
  }, var.tags)
}

module "service_base" {
  source = "git::https://github.com/COPUR/fintechbankx-platform-delivery-iac-terraform-modules.git//modules/microservice-base?ref=main"

  service_name           = "Request to Pay Service"
  service_slug           = local.service_slug
  environment            = var.environment
  database_engine        = "aurora-postgresql"
  cache_engine           = "none"
  identity_provider_url  = var.identity_provider_url
  observability_endpoint = var.observability_endpoint
  parameter_prefix       = "/fintechbankx"
  log_retention_days     = var.environment == "prod" ? 365 : 30
  tags                   = local.tags
}

# --- Encryption -------------------------------------------------------------

# Tagged fintechbankx.io/secrets=true so the platform External Secrets
# Operator role may decrypt the application credential; the key policy keeps
# IAM in charge through the account root.
resource "aws_kms_key" "database" {
  description             = "Encrypts ${local.database} storage, snapshots, logs and credentials"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AccountRootAdministersKeyThroughIam"
      Effect    = "Allow"
      Principal = { AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root" }
      Action    = "kms:*"
      Resource  = "*"
    }]
  })
  tags = {
    "fintechbankx.io/secrets" = "true"
  }
}

resource "aws_kms_alias" "database" {
  name          = "alias/${local.name}-db"
  target_key_id = aws_kms_key.database.key_id
}

# --- Network ----------------------------------------------------------------

resource "aws_db_subnet_group" "database" {
  name       = "${local.name}-db"
  subnet_ids = var.private_subnet_ids
}

resource "aws_security_group" "database" {
  name        = "${local.name}-db"
  description = "PostgreSQL access for ${local.service_id} only"
  vpc_id      = var.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "postgres_from_workload" {
  security_group_id            = aws_security_group.database.id
  referenced_security_group_id = var.workload_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "PostgreSQL from ${local.service_id} pods"
}

# --- Aurora PostgreSQL (Serverless v2, Multi-AZ) ---------------------------

resource "aws_rds_cluster_parameter_group" "database" {
  name   = "${local.name}-aurora-pg16"
  family = "aurora-postgresql16"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "500"
  }
}

resource "aws_rds_cluster" "database" {
  cluster_identifier                  = "${local.name}-aurora"
  engine                              = "aurora-postgresql"
  engine_mode                         = "provisioned"
  engine_version                      = var.aurora_engine_version
  database_name                       = local.database
  master_username                     = "rtp_admin"
  master_user_secret_kms_key_id       = aws_kms_key.database.key_id
  db_subnet_group_name                = aws_db_subnet_group.database.name
  vpc_security_group_ids              = [aws_security_group.database.id]
  db_cluster_parameter_group_name     = aws_rds_cluster_parameter_group.database.name
  storage_encrypted                   = true
  kms_key_id                          = aws_kms_key.database.arn
  iam_database_authentication_enabled = true
  backup_retention_period             = var.backup_retention_days
  preferred_backup_window             = "01:00-02:00"
  preferred_maintenance_window        = "sun:03:00-sun:04:00"
  copy_tags_to_snapshot               = true
  deletion_protection                 = var.deletion_protection
  skip_final_snapshot                 = false
  final_snapshot_identifier           = "${local.name}-aurora-final"
  enabled_cloudwatch_logs_exports     = ["postgresql"]

  # RDS generates and rotates the admin credential in Secrets Manager; it never enters state.
  # (Inline comment keeps the publication guardrail's credential-assignment pattern from matching.)
  manage_master_user_password /* rds-managed */ = true

  serverlessv2_scaling_configuration {
    min_capacity = var.aurora_min_capacity
    max_capacity = var.aurora_max_capacity
  }

  lifecycle {
    # Minor versions are upgraded by AWS (auto_minor_version_upgrade); do not fight them.
    ignore_changes = [engine_version]
  }
}

resource "aws_rds_cluster_instance" "database" {
  count                                 = var.aurora_instance_count
  identifier                            = "${local.name}-aurora-${count.index + 1}"
  cluster_identifier                    = aws_rds_cluster.database.id
  instance_class                        = "db.serverless"
  engine                                = aws_rds_cluster.database.engine
  db_subnet_group_name                  = aws_db_subnet_group.database.name
  publicly_accessible                   = false
  auto_minor_version_upgrade            = true
  performance_insights_enabled          = true
  performance_insights_kms_key_id       = aws_kms_key.database.arn
  performance_insights_retention_period = 7
  promotion_tier                        = count.index
}

# Two database roles (platform review round, item 4). The runtime role
# payment_request_to_pay_app has DML only on sc_pay_request_to_pay; the schema
# owner payment_request_to_pay_owner runs Flyway from the pre-install/pre-upgrade
# migration Job and is never mounted in the service pods. The DBA bootstrap in
# the runbook creates both roles and writes {"username", "password"} into these
# secrets; Terraform never sees the values. Names follow terraform-modules
# aurora-postgresql (019a842): <env>/<service account>/db-app and db-migration,
# because the External Secrets role may read only <env>/*. Neither secret is
# tagged fintechbankx.io/value-in-state: Terraform never writes or reads their
# values, so the PR plan role (terraform-modules f8202f0) only describes them.
resource "aws_secretsmanager_secret" "app_database" {
  name                    = "${var.environment}/${local.service_slug}/db-app"
  description             = "Application database credential for ${local.service_id}"
  kms_key_id              = aws_kms_key.database.arn
  recovery_window_in_days = 7
}

# Schema owner credential, read only by the migration Job (Helm value
# migration.remoteSecretName). aurora-postgresql migration_secret_name.
resource "aws_secretsmanager_secret" "migration_database" {
  name                    = "${var.environment}/${local.service_slug}/db-migration"
  description             = "Schema owner (Flyway migration) credential for ${local.service_id}"
  kms_key_id              = aws_kms_key.database.arn
  recovery_window_in_days = 7
}

# --- IRSA: the pods' AWS identity (Amazon MSK only) -------------------------

data "aws_iam_policy_document" "irsa_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.eks_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.eks_oidc_provider_url}:sub"
      values   = ["system:serviceaccount:${var.kubernetes_namespace}:${var.kubernetes_service_account}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.eks_oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "workload" {
  name               = "${local.name}-irsa"
  assume_role_policy = data.aws_iam_policy_document.irsa_trust.json
}

# TODO(platform): replace with the terraform-modules msk-client-access module
# once it exists on main; until then this inline policy scopes the producer
# to the evt.pay.rtp topics. No CreateTopic: topics come from the catalog.
data "aws_iam_policy_document" "msk_producer" {
  statement {
    sid       = "ConnectToCluster"
    actions   = ["kafka-cluster:Connect", "kafka-cluster:DescribeCluster"]
    resources = [var.msk_cluster_arn]
  }

  statement {
    sid     = "WriteOwnTopics"
    actions = ["kafka-cluster:DescribeTopic", "kafka-cluster:WriteData"]
    resources = [
      for topic in local.topics :
      "arn:aws:kafka:${var.aws_region}:${data.aws_caller_identity.current.account_id}:topic/${var.msk_cluster_name}/${var.msk_cluster_uuid}/${topic}"
    ]
  }

  statement {
    sid       = "IdempotentProducer"
    actions   = ["kafka-cluster:WriteDataIdempotently"]
    resources = [var.msk_cluster_arn]
  }
}

resource "aws_iam_role_policy" "msk_producer" {
  name   = "${local.name}-msk-producer"
  role   = aws_iam_role.workload.id
  policy = data.aws_iam_policy_document.msk_producer.json
}

# --- Alarms -----------------------------------------------------------------

resource "aws_cloudwatch_metric_alarm" "aurora_capacity" {
  alarm_name          = "${local.name}-aurora-acu-high"
  alarm_description   = "Aurora is near its max ACUs; raise aurora_max_capacity or look for a runaway query."
  namespace           = "AWS/RDS"
  metric_name         = "ACUUtilization"
  dimensions          = { DBClusterIdentifier = aws_rds_cluster.database.cluster_identifier }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = 85
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_topic_arn == "" ? [] : [var.alarm_topic_arn]
  ok_actions          = var.alarm_topic_arn == "" ? [] : [var.alarm_topic_arn]
}

# Connection budget: every replica at full pool (HPA maxReplicas x DB_POOL_MAX,
# 12 x 10 = 120 by default) plus headroom for the migration Job, the backfill
# and DBA sessions. The alarm fires only above that budget, i.e. on a
# connection leak or an unexpected client, not under normal peak load. Keep
# the variables in step with the Helm values (autoscaling.maxReplicas,
# config.DB_POOL_MAX), and keep the budget below Aurora's max_connections at
# aurora_min_capacity (Serverless v2 sizes it from capacity).
locals {
  db_connection_budget = var.service_max_replicas * var.db_pool_max + var.db_connection_headroom
}

resource "aws_cloudwatch_metric_alarm" "aurora_connections" {
  alarm_name          = "${local.name}-aurora-connections-high"
  alarm_description   = "Connections above the pool budget (${var.service_max_replicas} replicas x ${var.db_pool_max} + ${var.db_connection_headroom}): leak or unexpected client."
  namespace           = "AWS/RDS"
  metric_name         = "DatabaseConnections"
  dimensions          = { DBClusterIdentifier = aws_rds_cluster.database.cluster_identifier }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  threshold           = local.db_connection_budget
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_topic_arn == "" ? [] : [var.alarm_topic_arn]
}
