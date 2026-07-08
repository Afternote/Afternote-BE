import os
import shlex
import time

import boto3
from botocore.exceptions import ClientError

ec2 = boto3.client("ec2")
rds = boto3.client("rds")
ssm = boto3.client("ssm")

EC2_INSTANCE_ID = os.environ["EC2_INSTANCE_ID"]
RDS_INSTANCE_ID = os.environ["RDS_INSTANCE_ID"]
DEPLOY_PATH = os.environ["DEPLOY_PATH"]
EC2_USER = os.environ.get("EC2_USER", "ec2-user")
SNS_TOPIC_ARN = os.environ.get("SNS_TOPIC_ARN", "")

# deploy.yml 과 동일하게 ec2-user 로 compose 실행 (SSM 기본 root 는 compose plugin 없음)
DOCKER_START_SCRIPT = (
    f"set -e; cd {DEPLOY_PATH}; "
    "if docker compose version >/dev/null 2>&1; then "
    "docker compose up -d --remove-orphans && docker compose ps; "
    "elif command -v docker-compose >/dev/null 2>&1; then "
    "docker-compose up -d --remove-orphans && docker-compose ps; "
    "else echo 'docker compose not found' >&2; exit 1; fi"
)


def notify_failure(message: str) -> None:
    if not SNS_TOPIC_ARN:
        return
    sns = boto3.client("sns")
    sns.publish(
        TopicArn=SNS_TOPIC_ARN,
        Subject="[Afternote] Start environment failed",
        Message=message,
    )


def wait_rds_available(db_id: str, max_wait: int = 600) -> None:
    for _ in range(max_wait // 15):
        response = rds.describe_db_instances(DBInstanceIdentifier=db_id)
        status = response["DBInstances"][0]["DBInstanceStatus"]
        print(f"RDS {db_id} status: {status}")
        if status == "available":
            return
        if status in ("failed", "incompatible-restore", "incompatible-network"):
            raise RuntimeError(f"RDS {db_id} is in bad state: {status}")
        time.sleep(15)
    raise TimeoutError(f"RDS {db_id} not available within {max_wait}s")


def wait_ssm_online(instance_id: str, max_wait: int = 180) -> None:
    """EC2 cold start 후 SSM Agent가 Online 될 때까지 대기."""
    for attempt in range(max_wait // 10):
        response = ssm.describe_instance_information(
            Filters=[{"Key": "InstanceIds", "Values": [instance_id]}]
        )
        instances = response.get("InstanceInformationList", [])
        if instances and instances[0].get("PingStatus") == "Online":
            print(f"SSM Online for {instance_id}")
            return
        print(f"Waiting for SSM Online (attempt {attempt + 1})...")
        time.sleep(10)
    raise RuntimeError(
        f"EC2 {instance_id} is not SSM Online after {max_wait}s. "
        "Check Systems Manager Fleet Manager and IAM AmazonSSMManagedInstanceCore."
    )


def wait_ssm_command(command_id: str, instance_id: str, max_wait: int = 300) -> None:
    for attempt in range(max_wait // 5):
        try:
            invocation = ssm.get_command_invocation(
                CommandId=command_id,
                InstanceId=instance_id,
            )
        except ClientError as exc:
            code = exc.response.get("Error", {}).get("Code", "")
            if code == "InvocationDoesNotExist":
                print(f"SSM invocation not registered yet (attempt {attempt + 1})")
                time.sleep(5)
                continue
            raise

        status = invocation["Status"]
        print(f"SSM command status: {status}")
        if status == "Success":
            print(invocation.get("StandardOutputContent", ""))
            return
        if status in ("Failed", "Cancelled", "TimedOut"):
            stderr = invocation.get("StandardErrorContent", "")
            stdout = invocation.get("StandardOutputContent", "")
            raise RuntimeError(f"SSM command {status}. stdout={stdout} stderr={stderr}")
        time.sleep(5)

    raise TimeoutError(f"SSM command {command_id} did not finish within {max_wait}s")


def handler(event, context):
    try:
        print(f"Starting RDS {RDS_INSTANCE_ID}")
        try:
            rds.start_db_instance(DBInstanceIdentifier=RDS_INSTANCE_ID)
        except rds.exceptions.InvalidDBInstanceStateFault as exc:
            print(f"RDS start skipped: {exc}")

        wait_rds_available(RDS_INSTANCE_ID)

        print(f"Starting EC2 {EC2_INSTANCE_ID}")
        ec2.start_instances(InstanceIds=[EC2_INSTANCE_ID])
        waiter = ec2.get_waiter("instance_running")
        waiter.wait(
            InstanceIds=[EC2_INSTANCE_ID],
            WaiterConfig={"Delay": 15, "MaxAttempts": 40},
        )

        wait_ssm_online(EC2_INSTANCE_ID)

        print("Starting docker compose via SSM")
        command = ssm.send_command(
            InstanceIds=[EC2_INSTANCE_ID],
            DocumentName="AWS-RunShellScript",
            Parameters={
                "commands": [
                    "sudo systemctl start docker 2>/dev/null || true",
                    f"sudo -u {EC2_USER} bash -lc {shlex.quote(DOCKER_START_SCRIPT)}",
                ]
            },
            TimeoutSeconds=300,
        )
        command_id = command["Command"]["CommandId"]
        time.sleep(3)
        wait_ssm_command(command_id, EC2_INSTANCE_ID)

        return {
            "status": "started",
            "ec2_instance_id": EC2_INSTANCE_ID,
            "rds_instance_id": RDS_INSTANCE_ID,
            "ssm_command_id": command_id,
        }
    except Exception as exc:
        notify_failure(str(exc))
        raise
