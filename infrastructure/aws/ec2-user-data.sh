#!/bin/bash
# SpringLens EC2 Bootstrap Script (Amazon Linux 2023 / Spot Instance)
# Use as EC2 User Data when launching the instance.
#
# Instance type: t3.xlarge (4 vCPU, 16 GB RAM)
# AMI: Amazon Linux 2023 (al2023-ami-*)
# Storage: 30 GB gp3
# Security Group: open ports 22, 3000, 8081-8085

set -euo pipefail
exec > /var/log/springlens-setup.log 2>&1

echo "=== SpringLens EC2 Setup ==="

# ── 1. System packages ───────────────────────────────────────────────────────
dnf update -y
dnf install -y git java-21-amazon-corretto-devel gcc-c++ make

# ── 2. Node.js 20 ────────────────────────────────────────────────────────────
curl -fsSL https://rpm.nodesource.com/setup_20.x | bash -
dnf install -y nodejs

# ── 3. PostgreSQL 16 ─────────────────────────────────────────────────────────
dnf install -y postgresql16-server postgresql16
postgresql-setup --initdb
# Allow local password auth
sed -i 's/ident$/md5/g' /var/lib/pgsql/data/pg_hba.conf
sed -i 's/peer$/md5/g' /var/lib/pgsql/data/pg_hba.conf
systemctl enable --now postgresql

# Create user and databases
sudo -u postgres psql -c "CREATE USER springlens WITH PASSWORD 'springlens_dev' CREATEDB;"
for db in springlens_auth springlens_ingestion springlens_analysis springlens_recommendation springlens_notification; do
  sudo -u postgres psql -c "CREATE DATABASE $db OWNER springlens;" || true
done

# ── 4. Redis ──────────────────────────────────────────────────────────────────
dnf install -y redis6
systemctl enable --now redis6

# ── 5. Kafka (KRaft mode, no ZooKeeper) ──────────────────────────────────────
KAFKA_VERSION="3.7.0"
SCALA_VERSION="2.13"
cd /opt
curl -sO "https://downloads.apache.org/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz"
tar xzf "kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz"
ln -s "kafka_${SCALA_VERSION}-${KAFKA_VERSION}" kafka
rm -f "kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz"

# Configure KRaft
CLUSTER_ID=$(/opt/kafka/bin/kafka-storage.sh random-uuid)
/opt/kafka/bin/kafka-storage.sh format -t "$CLUSTER_ID" -c /opt/kafka/config/kraft/server.properties

# Create systemd service for Kafka
cat > /etc/systemd/system/kafka.service <<'UNIT'
[Unit]
Description=Apache Kafka (KRaft)
After=network.target

[Service]
Type=simple
User=root
Environment="KAFKA_HEAP_OPTS=-Xmx512m -Xms512m"
ExecStart=/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/kraft/server.properties
ExecStop=/opt/kafka/bin/kafka-server-stop.sh
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now kafka

# Wait for Kafka to be ready
for i in {1..30}; do
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list >/dev/null 2>&1 && break
  sleep 2
done

# ── 6. Clone and build SpringLens ─────────────────────────────────────────────
cd /home/ec2-user
# NOTE: Replace with your actual repo URL
# git clone https://github.com/YOUR_ORG/cloud_photo.git springlens
# For now, create a placeholder directory — you'll scp or git clone manually
mkdir -p springlens
chown -R ec2-user:ec2-user springlens

echo "=== Infrastructure ready. Upload your code and run deploy.sh ==="
