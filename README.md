# camunda-bpmn-order-management
An order management system using camunda and it's bpmn modeler to orchestrate microservices.

## Run mysql database

```sq
sudo docker pull mysql/mysql-server:latest

```

## Run the payment external task locally

```sq
mvn clean install
java -jar target/payment.jar
```

## Build camunda engine image

```sq
docker build --tag=camunda-engine-app .

docker run -d --name camunda-engine-app-container -p8989:8989 camunda-engine-app:latest

docker ps

docker container logs container_id

docker stop container_id
```

## Run LocalStack (Development)

1 - Download docker-compose.yml file:
https://github.com/localstack/localstack/blob/master/docker-compose.yml

2 - Run local stack:

```sq
docker-compose up
```

## Create local payment AWS SQS Queue

```sq
awslocal sqs create-queue --queue-name loc-saeast-paymentorder-request
```
