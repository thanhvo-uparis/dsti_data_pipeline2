# Run Batch with AWS
Follow these steps to execute the batch on Amazon Web Services (AWS) using the _EMR Serverless_ service.

---

**NOTE:** Unfortunately, the AWS execution of the batch fails. I am 99% certain this is due to the way the batch is 
designed: Results are to be written to a temporary directory (see [DataWriters.scala](../tracker/src/DataWriters.scala))
 and then copied and renamed to generate the final output. 

This works well when running in standalone or in a spark cluster on the local machine. However, When running in this 
serverless context, the application  terminates after the temporary files are written (and no output is ever written 
back to S3):

```
...
22/09/11 14:31:24 INFO FileOutputCommitter: Saved output of task 'attempt_202209111431243648752553778838390_0008_m_000000_15' to file:/home/hadoop/data/temp
22/09/11 14:31:24 INFO SparkHadoopMapRedUtil: attempt_202209111431243648752553778838390_0008_m_000000_15: Committed
22/09/11 14:31:24 INFO Executor: Finished task 0.0 in stage 8.0 (TID 15). 5935 bytes result sent to driver
22/09/11 14:31:24 INFO CoarseGrainedExecutorBackend: Driver commanded a shutdown
22/09/11 14:31:24 INFO MemoryStore: MemoryStore cleared
22/09/11 14:31:24 INFO BlockManager: BlockManager stopped
22/09/11 14:31:24 INFO ShutdownHookManager: Shutdown hook called
22/09/11 14:31:24 INFO ShutdownHookManager: Deleting directory /tmp/spark-d83645ca-2b37-4e76-a565-af910eb81465
```

This is unfortunate, and I am very certain that aside from this issue the below instructions to run the batch on AWS 
are correct. Sadly, I don't have the time to re-design the batch to make it suitable for cloud execution. I'll know 
better for next time, though.

---

## Requirements
- An account with [Amazon Web Services](https://aws.amazon.com/)
- The [AWS CLI](https://aws.amazon.com/cli/) must be configured and updated

## Usage
- Note that these steps follow the [Amazon EMR Serverless User Guide](https://docs.aws.amazon.com/emr/latest/EMR-Serverless-UserGuide/getting-started.html#gs-cli)
- Also note that following these steps will incur some cost on your AWS account
- All commands are to be executed in bash from the project root

### Prepare Storage in S3
- To execute the batch on EMR Serverless, we need to prepare an S3 bucket
- The bucket will hold our spark application .jar file and our input files
- Our batch will write its outputs to the same S3 bucket

```bash
# S3 parameters
S3_BUCKET_NAME=my-unique-bucket-name
S3_ACL_SETTING=private 
S3_BUCKET_REGION=eu-west-1
S3_BUCKET_ARN=arn:aws:s3:::$S3_BUCKET_NAME

# Local file parameters
SPARK_TRACKER=./out/tracker/assembly.dest/out.jar
SRC_CITIES=./data/in/brazil_covid19_cities.csv
SRC_STATES=./data/in/brazil_covid19.csv

# Bucket creation
aws s3api create-bucket \
	--bucket $S3_BUCKET_NAME \
	--acl $S3_ACL_SETTING \
	--create-bucket-configuration LocationConstraint=$S3_BUCKET_REGION \
	--region $S3_BUCKET_REGION
	
# .jar file upload
aws s3api put-object \
	--bucket $S3_BUCKET_NAME \
	--key spark/tracker.jar \
	--body $SPARK_TRACKER
	
# Covid19 data by cities
aws s3api put-object \
	--bucket $S3_BUCKET_NAME \
	--key data/in/brazil_covid19_cities.csv \
	--body $SRC_CITIES
	
# Covid19 data by state
aws s3api put-object \
	--bucket $S3_BUCKET_NAME \
	--key data/in/brazil_covid19.csv \
	--body $SRC_STATES
```

### Configure Execution Role & Policies in IAM
- We need to create an execution role with proper policies to run our batch on EMR Serverless
- Note that this step requires the `S3_BUCKET_NAME` variable to be set

```bash
# More parameters 
AWS_ACCOUNT_ID=$(aws sts get-caller-identity | grep -oP '(?<="Account":\s")[0-9]*')
IAM_ROLE=EMRServerlessS3RuntimeRole
IAM_ROLE_ARN=arn:aws:iam::$AWS_ACCOUNT_ID:role/$IAM_ROLE
IAM_POLICY=EMRServerlessS3AndGlueAccessPolicy
IAM_POLICY_ARN=arn:aws:iam::$AWS_ACCOUNT_ID:policy/$IAM_POLICY

# Create a runtime role 
aws iam create-role \
    --role-name $IAM_ROLE\
    --assume-role-policy-document file://aws/emr-serverless-trust-policy.json

# Create an Access Policy Document
cat <<EOF >./aws/emr-access-policy.json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "ReadAccessForEMRSamples",
            "Effect": "Allow",
            "Action": [
                "s3:GetObject",
                "s3:ListBucket"
            ],
            "Resource": [
                "arn:aws:s3:::*.elasticmapreduce",
                "arn:aws:s3:::*.elasticmapreduce/*"
            ]
        },
        {
            "Sid": "FullAccessToOutputBucket",
            "Effect": "Allow",
            "Action": [
                "s3:PutObject",
                "s3:GetObject",
                "s3:ListBucket",
                "s3:DeleteObject"
            ],
            "Resource": [
                "$S3_BUCKET_ARN",
                "$S3_BUCKET_ARN/*"
            ]
        },
        {
            "Sid": "GlueCreateAndReadDataCatalog",
            "Effect": "Allow",
            "Action": [
                "glue:GetDatabase",
                "glue:CreateDatabase",
                "glue:GetDataBases",
                "glue:CreateTable",
                "glue:GetTable",
                "glue:UpdateTable",
                "glue:DeleteTable",
                "glue:GetTables",
                "glue:GetPartition",
                "glue:GetPartitions",
                "glue:CreatePartition",
                "glue:BatchCreatePartition",
                "glue:GetUserDefinedFunctions"
            ],
            "Resource": ["*"]
        }
    ]
}
EOF

# Create an access policy
aws iam create-policy \
    --policy-name $IAM_POLICY \
    --policy-document file://aws/emr-access-policy.json
    
# Attach the access policy to the execution role
aws iam attach-role-policy \
    --role-name $IAM_ROLE \
    --policy-arn $IAM_POLICY_ARN
```
### Execute on AWS
- With everything configured we can now create our EMR Serverless application
- Note that EMR Serverless is not available in all regions 
- The EMR Serverless application should be hosted in the same reason as our S3 bucket

```bash
# Some more parameters 
EMR_APP_NAME=my-application-name 
EMR_APP_LABEL=emr-6.6.0   # Supports Spark 3.3.0
EMR_APP_TYPE="SPARK"
EMR_APP_REGION=$S3_BUCKET_REGION

# Create an EMR Serverless Application
aws emr-serverless create-application \
  --release-label $EMR_APP_LABEL \
  --type $EMR_APP_TYPE \
  --name $EMR_APP_NAME \
  --region $EMR_APP_REGION
  
# Retrieve the application ID 
# NOTE: This only works if you have exactly 1 EMR Serverless application in the region
EMR_APP_ID=$(aws emr-serverless list-applications --region $EMR_APP_REGION | grep -oP '(?<="id":\s")[a-z0-9]*')

# View the status of the application
# Spark job can be submitted once "state" is "CREATED"
aws emr-serverless get-application \
  --application-id $EMR_APP_ID \
  --region $EMR_APP_REGION
  
# Submit the aggregate job
aws emr-serverless start-job-run \
  --application-id $EMR_APP_ID \
  --execution-role $IAM_ROLE_ARN \
  --name $EMR_APP_NAME \
  --region $EMR_APP_REGION \
  --job-driver '{
    "sparkSubmit": {
      "entryPoint": "'s3://${S3_BUCKET_NAME}/spark/tracker.jar'",
      "entryPointArguments": [
        "agg",
        "'s3://$S3_BUCKET_NAME/data/in/brazil_covid19_cities.csv'",
        "new_brazil_covid19.csv"
      ],
      "sparkSubmitParameters": "--class TrackerCli"
    }
  }'

# Submit the compare job
aws emr-serverless start-job-run \
  --application-id $EMR_APP_ID \
  --execution-role $IAM_ROLE_ARN \
  --name $EMR_APP_NAME \
  --region $EMR_APP_REGION \
  --job-driver '{
    "sparkSubmit": {
      "entryPoint": "'s3://${S3_BUCKET_NAME}/spark/tracker.jar'",
      "entryPointArguments": [
        "cmp",
        "'s3://$S3_BUCKET_NAME/data/in/brazil_covid19.csv'",
        "'s3://$S3_BUCKET_NAME/data/out/new_brazil_covid19.csv'"
      ],
      "sparkSubmitParameters": "--class TrackerCli"
    }
  }'
  
# Download the results
aws s3 cp s3://$S3_BUCKET_NAME/data/out/new_brazil_covid19.csv ./data/out/
aws s3 cp s3://$S3_BUCKET_NAME/data/out/diff_report.json ./data/out/
```
## Cleanup
- Delete all resources to avoid unexpected cost

```bash
# Delete the application
aws emr-serverless delete-application \
    --application-id $EMR_APP_ID \
    --region $EMR_APP_REGION

# Clear out S3 bucket and delete
aws s3 rm s3://S3_BUCKET_NAME --recursive
aws s3api delete-bucket --bucket S3_BUCKET_NAME

# Detach policy from role before deletion
aws iam detach-role-policy \
    --role-name $IAM_ROLE \
    --policy-arn $IAM_POLICY_ARN
    
# Delete role 
aws iam delete-role \
    --role-name $IAM_ROLE 

# Delete policy
aws iam delete-policy \
    --policy-arn $IAM_POLICY_ARN
```
