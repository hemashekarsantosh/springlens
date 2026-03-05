#!/bin/bash
# SpringLens — Launch EC2 Spot Instance
#
# Prerequisites:
#   - AWS CLI configured (aws configure)
#   - A key pair created: aws ec2 create-key-pair --key-name springlens-dev --query 'KeyMaterial' --output text > springlens-dev.pem
#   - chmod 400 springlens-dev.pem
#
# Usage: ./launch-spot.sh

set -euo pipefail

KEY_NAME="${KEY_NAME:-springlens-dev}"
INSTANCE_TYPE="${INSTANCE_TYPE:-t3.xlarge}"
REGION="${AWS_REGION:-us-east-1}"
AMI_ID=""

echo "━━━ SpringLens Spot Instance Launcher ━━━━━━━━━━━━━━━━"

# ── 1. Get latest Amazon Linux 2023 AMI ──────────────────────────────────────
echo "⧖ Finding latest AL2023 AMI in $REGION..."
AMI_ID=$(aws ec2 describe-images \
  --region "$REGION" \
  --owners amazon \
  --filters "Name=name,Values=al2023-ami-*-x86_64" "Name=state,Values=available" \
  --query 'sort_by(Images, &CreationDate)[-1].ImageId' \
  --output text)
echo "  AMI: $AMI_ID"

# ── 2. Create security group ─────────────────────────────────────────────────
SG_NAME="springlens-dev-sg"
SG_ID=$(aws ec2 describe-security-groups \
  --region "$REGION" \
  --filters "Name=group-name,Values=$SG_NAME" \
  --query 'SecurityGroups[0].GroupId' \
  --output text 2>/dev/null || echo "None")

if [ "$SG_ID" = "None" ] || [ -z "$SG_ID" ]; then
  echo "⧖ Creating security group..."
  SG_ID=$(aws ec2 create-security-group \
    --region "$REGION" \
    --group-name "$SG_NAME" \
    --description "SpringLens dev - SSH, frontend, APIs" \
    --query 'GroupId' \
    --output text)

  # SSH
  aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
    --protocol tcp --port 22 --cidr 0.0.0.0/0
  # Frontend
  aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
    --protocol tcp --port 3000 --cidr 0.0.0.0/0
  # Backend APIs
  aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
    --protocol tcp --port 8081-8085 --cidr 0.0.0.0/0
fi
echo "  Security group: $SG_ID"

# ── 3. Launch spot instance ──────────────────────────────────────────────────
echo "⧖ Launching $INSTANCE_TYPE spot instance..."
INSTANCE_ID=$(aws ec2 run-instances \
  --region "$REGION" \
  --image-id "$AMI_ID" \
  --instance-type "$INSTANCE_TYPE" \
  --key-name "$KEY_NAME" \
  --security-group-ids "$SG_ID" \
  --block-device-mappings '[{"DeviceName":"/dev/xvda","Ebs":{"VolumeSize":30,"VolumeType":"gp3"}}]' \
  --instance-market-options '{"MarketType":"spot","SpotOptions":{"SpotInstanceType":"persistent","InstanceInterruptionBehavior":"stop"}}' \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=springlens-dev}]" \
  --user-data file://ec2-user-data.sh \
  --query 'Instances[0].InstanceId' \
  --output text)
echo "  Instance: $INSTANCE_ID"

# ── 4. Wait for instance to be running ───────────────────────────────────────
echo "⧖ Waiting for instance to start..."
aws ec2 wait instance-running --region "$REGION" --instance-ids "$INSTANCE_ID"

PUBLIC_IP=$(aws ec2 describe-instances \
  --region "$REGION" \
  --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' \
  --output text)

echo ""
echo "━━━ Instance Ready ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  Instance ID:  $INSTANCE_ID"
echo "  Public IP:    $PUBLIC_IP"
echo "  Spot price:   ~\$0.05/hr"
echo ""
echo "  Wait ~5 min for user-data to finish, then:"
echo ""
echo "  1. Upload code:"
echo "     scp -i ${KEY_NAME}.pem -r ../../../ ec2-user@${PUBLIC_IP}:~/springlens/"
echo ""
echo "  2. SSH in:"
echo "     ssh -i ${KEY_NAME}.pem ec2-user@${PUBLIC_IP}"
echo ""
echo "  3. Set GitHub OAuth and run:"
echo "     export GITHUB_CLIENT_ID=your_id"
echo "     export GITHUB_CLIENT_SECRET=your_secret"
echo "     cd ~/springlens && ./infrastructure/aws/deploy.sh"
echo ""
echo "  4. Update GitHub OAuth callback URL to:"
echo "     http://${PUBLIC_IP}:3000/api/auth/callback/github"
echo ""
echo "  To stop (saves money, keeps data):"
echo "     aws ec2 stop-instances --instance-ids $INSTANCE_ID"
echo ""
echo "  To resume:"
echo "     aws ec2 start-instances --instance-ids $INSTANCE_ID"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
