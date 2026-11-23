import json
import os

import boto3

ec2 = boto3.client("ec2")
rds = boto3.client("rds")


def handler(event, context):
    ec2_id = os.environ["EC2_INSTANCE_ID"]
    rds_id = os.environ["RDS_INSTANCE_ID"]

    print(f"Stopping EC2 {ec2_id}")
    ec2.stop_instances(InstanceIds=[ec2_id])

    print(f"Stopping RDS {rds_id}")
    try:
        rds.stop_db_instance(DBInstanceIdentifier=rds_id)
    except rds.exceptions.InvalidDBInstanceStateFault as exc:
        print(f"RDS stop skipped (already stopped/stopping): {exc}")

    return {"status": "stopped", "ec2_instance_id": ec2_id, "rds_instance_id": rds_id}
