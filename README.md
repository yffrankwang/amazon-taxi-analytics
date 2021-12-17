Taxi Data Analysis System Architecture
=======================================================

## Brief

ABC company is a ride hailing company, they have large volume of subscribe users using their mobile app to get transportation services from local drivers. The mobile app for passengers and drivers will upload activities data to server for data analyst.


## Target User Group

1. Business analyst for trends
2. Operation analyst to decide the dynamic pricing
3. Drivers to understand the predicted demands in near future


## Requirements

1. Ingest real-time data from driver mobile app.
2. Perform data analysis (feature engineering) for data ETL.
3. Illustrate request and demands trends, especially for the gap trends of taxi for data analyst for insight discovery.
4. Provide graphic illustration for operators to understand the changes of demands gap in different area (location ID), and display a short 10-min prediction in graphics.
5. Simulate a real-time display of GUI on 10-min based data set for operators.
6. Let drivers to see the demands level in 10 min drive distance. Display the past and future in graphics.
7. Setup a portal for different users to login to watch different GUI.


## Scale

At least **10 thousand** drivers’ concurrencies.


## Architecture on AWS

### Diagram

![](https://github.com/yffrankwang/amazon-taxi-analytics/raw/master/taxi-data-analysis-system.jpg)


### Data Flow

1. Driver's mobile app send real-time event data to web application hosted on EC2 instances.
2. Web application accept then real-time event data and sent it to Amazon Kinesis Data Stream.
3. Amazon Kinesis Data Analystic for Java Application retrieve data from Kinesis Data Stream to do 10 minutes time window Map-Reduce data process, and save the transformed data to Amazon OpenSearch Service (real-time data index) and Amazon S3 bucket.
4. Amazon Event Bridge trigger a weekly job to launch Amazon SageMaker to retrieve the transformed data from Amazon S3 bucket, use DeepAR algorithm to train the time series based data and save the forecasting time series data to Amazon OpenSearch Service (forecasting data index).
5. End users signin the portal web application hosted on EC2 instances (The user identity is managed by Amazon Cognito) to do some trend analysis. The web application accepts the user requests, retrieve data from OpenSearch Service, generates charts, and displays them to users.


### AWS Services Used
1. [Amazon EC2](https://aws.amazon.com/amazon/ec2)
1. [Amazon Kinesis Data Stream](https://aws.amazon.com/kinesis/data-streams/)
1. [Amazon Kinesis Data Analytics](https://aws.amazon.com/kinesis/data-analytics/) Flink Application
1. [Amazon S3](https://aws.amazon.com/s3/)  Bucket
1. [Amazon OpenSearch Service](https://aws.amazon.com/opensearch-service/)
1. [Amazon SageMaker](https://aws.amazon.com/amazon/sagemaker)
1. [Amazon EventBridge](https://aws.amazon.com/jp/eventbridge/)

### Cost Estimate

**10 thousand** request / 1 second.

#### Kinesis Data Stream
- 0.50 KB / 1024 KB to MB conversion factor = 0.00048828 MB (Record size)
- 0.00048828 MB x 10,000 records per sec = 4.88 MB/sec (Data ingress rate)
- 4.88 MB/sec (Data ingress rate) / 1 MB per second per shard ingress capacity = 4.88 shards needed for ingress
- 4.88 MB/sec (Data ingress rate) x 4 consumer applications = 19.52 MB/sec (Data egress rate)
- 19.52 MB/sec / 2 MB per second per shard egress capacity = 9.76 shards needed for egress
- 10,000 records per sec / 1000 factor for records per shard = 10.00 shards needed for records
- Max (4.88 shards needed for ingress, 9.76 shards needed for egress, 10.000 shards needed for records) = 10.00 Number of shards
- RoundUp (10.000) = 10 shards
- 10 shards x 730 hours in a month = 7,300.00 Shard hours per month
- 7,300.00 Shard hours per month x 0.015 USD = 109.50 USD
- **Shard hours per month cost: 109.50 USD**
- 0.50 KB / 25 Payload Unit factor = 0.02 PUT Payload Units fraction
- RoundUp (0.02) = 1 PUT Payload Units
- 1 PUT Payload Units x 10,000 records per sec x 2628000 seconds in a month = 26,280,000,000.00 PUT Payload Units per month
- 26,280,000,000.00 PUT Payload Units x 0.000000014 USD = 367.92 USD
- **PUT Payload Units per month cost: 367.92 USD**
- 109.50 USD + 367.92 USD = 477.42 USD
- **Kinesis data stream cost (monthly): 477.42 USD**


#### Kinesis Flink Applications
- 4 KPUs + 1 additional orchestration KPU(s) = 5 billable KPUs per hour
- 1 applications x 5 KPUs x 730 Hours in a month = 3,650 KPU hours per month
- 3,650 KPU hours x 0.11 USD = 401.50 USD for KPUs
- **Apache Flink KPU cost: 401.50 USD**
- 1 applications x 4 KPUs x 50 GB running application storage = 200 GBs per month
- 200 GB x 0.10 USD = 20.00 USD for running application storage
- **Running application storage cost: 20.00 USD**
- 100 GB x 0.023 USD = 2.30 USD for durable application backup storage
- RoundUp (2.3000) = 2.3000000000000003 USD for durable application backup storage rounded up to nearest cent
- **Durable application backup storage cost: 2.3000000000000003 USD**
- 401.50 USD + 20.00 USD + 2.3000000000000003 USD = 423.80 USD per month
- **Kinesis Data Analytics for Apache Flink applications cost (monthly): 423.80 USD**


#### EC2 Instances
- 4 instances (a1.xlarge) x 0.102 USD x 730 hours in a month = 297.84 USD (monthly onDemand cost)
- **Amazon EC2 On-Demand instances (monthly): 297.84 USD**
- 30 GB x 0.10 USD x 4 instances = 12.00 USD (EBS Storage Cost)
- **EBS Storage Cost: 12.00 USD**
- **Amazon Elastic Block Storage (EBS) pricing (monthly): 12.00 USD**


#### OpenSearch Service
- 2 instances (c4 2xlarge.search) x 0.58700000 USD hourly x 730 hours in a month = 857.02 USD (Amazon OpenSearch Service data instance cost)
- **Amazon OpenSearch Service data instance cost (monthly): 857.02 USD**
- 1 instance (c4 2xlarge.search) x 0.58700000 USD hourly x 730 hours in a month = 428.51 USD (Amazon OpenSearch Service dedicated master instance cost)
- **Amazon OpenSearch Service dedicated master instance cost (monthly): 428.51 USD**
- Storage amount: 1 TB x 1024 GB in a TB = 1024 GB
- Pricing calculations: 1,024 GB x 0.135 USD x 2 instances = 276.48 USD (EBS Storage Cost)
- **Amazon OpenSearch Service EBS storage cost (monthly): 276.48 USD**


#### SageMarker
- 1 data scientist(s) x 1 Studio Notebook instance(s) = 1.00 Studio Notebook instance(s)
- 1.00 Studio Notebook instance(s) x 24 hours per day x 5 days per month = 120.00 SageMaker Studio Notebook hours per month
- 120.00 hours per month x 2.448 USD per hour instance cost = 293.76 USD(monthly On-Demand cost)
- **Total cost for Studio Notebooks (monthly): 293.76 USD**


**Total Monthly Cost: 3,066.83 USD**




## DEMO Application

We use [New York City Taxi and Limousine Commission (TLC) Trip Record Data](https://registry.opendata.aws/nyc-tlc-trip-records-pds/) from AWS public datasets as source data, and build a Kinesis Data Stream Producer application to emulate a real-time trip event data stream by replaying the dataset. The processed data and visualization can be viewed in the link below.

- [Demo](http://ec2-34-199-178-181.compute-1.amazonaws.com/)
- [Dashboard](http://ec2-34-199-178-181.compute-1.amazonaws.com/_plugin/kibana/app/kibana#/dashboard/nyc-tlc-dashboard)


To see the described demo application in action, execute the following AWS CloudFormation template in your own AWS account. 

[Launch Stack](https://console.aws.amazon.com/cloudformation/home#/stacks/new?stackName=kinesis-analytics-taxi-consumer&templateURL=https://s3.amazonaws.com/yfw-useast1/artifacts/kinesis-analytics-taxi-consumer/cfn-templates/kinesis-analytics-taxi-consumer.yml)

The entire process of building the application and creating the infrastructure takes about 20 minutes. After the AWS CloudFormation stack is created, the Flink application has been deployed as a Kinesis Data Analytics for Java application. It then waits for events in the data stream to arrive. Checkpointing is enabled so that the application can seamlessly recover from failures of the underlying infrastructure while Kinesis Data Analytics for Java Applications manages the checkpoints on your behalf. In addition, automatic scaling is configured so that Kinesis Data Analytics for Java Applications automatically allocates or removes resources and scales the application (that is, it adapts its parallelism) in response to changes in the incoming traffic.

To populate the Kinesis data stream, we use a Java application that replays a public dataset of historic taxi trips made in New York City into the data stream. The Java application has already been downloaded to an Amazon EC2 instance that was provisioned by AWS CloudFormation. You just need to connect to the instance and execute the JAR file to start ingesting events into the stream.

You can obtain all of the following commands, including their correct parameters, from the output section of the AWS CloudFormation template that you executed previously.

```sh
$ ssh ec2-user@«Replay instance DNS name»

$ java -jar amazon-kinesis-replay-*.jar -streamName «Kinesis data stream name» -streamRegion «AWS region» -speedup 3600
```

You can then go ahead and inspect the derived data through the Kibana dashboard that has been created. Or you can create your own visualizations to explore the data in Kibana.

https://«Elasticsearch endpoint»/_plugin/kibana/app/kibana#/dashboard/nyc-tlc-dashboard


**This DEMO only completes the Kinesis Data Stream Producer, Kinesis Data Analytics for Java application， OpenSearch. The SageMaker DeepAR training / forecasting for time series are not completed.**

## Source

#### Kinesis Data Stream Producer
https://github.com/yffrankwang/amazon-kinesis-replay

Use NYC-TLC dataset to replaying the real-time trip event data stream.


#### Kinesis Analytics Flink Application
https://github.com/yffrankwang/amazon-kinesis-analytics-taxi-consumer

The Kinesis Data Analytics for Java Application (Apache Flink).
Comsume the real-time event from Kinesis Data Stream, do 10 minutes time window Map-Reduce job, save the transformed data to S3 for later SageMaker DeepAR training, save the transformed data to OpenSearch Service for search and visualization.


#### SageMaker DeepAR Jupyter Notebook
**INCOMPLETE**
https://github.com/yffrankwang/amazon-taxi-analytics/blob/master/sagemaker/deepar_taxi_pickup_count.py

A Jupyter Notebook for training data use SageMaker DeepAR algorithm.


## TODO:

- SageMaker: complete the Jupyter Notebook for training the data and do prediction, save to forecasting data to OpenSearch (forecasting data index).
- Cognito: setup cognito and integrate with the web application for user signin.
- WEB APP: user signin UI/API connected with cognito.
- WEB APP: a API for real-time drive event (collect and send the event to Kinesis Data Stream).
- WEB APP: different UI for different user groups.

