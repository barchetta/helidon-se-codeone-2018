
# Helidon Example: quickstart-se

This example implements a simple Hello World REST service.

## Prerequisites

1. Maven 3.5 or newer
2. Java SE 8 or newer
3. Docker 17 or newer to build and run docker images
4. Kubernetes minikube v0.24 or newer to deploy to Kubernetes (or access to a K8s 1.7.4 or newer cluster)
5. Kubectl 1.7.4 or newer to deploy to Kubernetes

Verify prerequisites
```
java --version
mvn --version
docker --version
minikube version
kubectl version --short
```

## Build

```
mvn package
```

## Start the application

```
java -jar target/quickstart-se.jar
```

## Exercise the application

```
# Get the default greeting
curl -X GET http://localhost:8080/greet
{"message":"Hello World!"}

# Get a greeting for Joe
curl -X GET http://localhost:8080/greet/Joe
{"message":"Hello Joe!"}

# Change the greeting using PUT
curl -X PUT http://localhost:8080/greet/greeting/Hola
{"gretting":"Hola"}

# Get a greeting for Jose, notice Hello is now Hola
curl -X GET http://localhost:8080/greet/Jose
{"message":"Hola Jose!"}

# Change greeting by POSTing JSON
curl -X POST -d '{"greeting" : "Howdy"}' http://localhost:8080/greet/greeting

# Change greeting by POSTing JSON to a slow handler
# Using asynchronous processing
curl -X POST -d '{"greeting" : "Hi"}' http://localhost:8080/greet/slowgreeting

```

## Build the Docker Image

```
docker build -t quickstart-se target
```

## Start the application with Docker

```
docker run --rm -p 8080:8080 quickstart-se:latest
```

Exercise the application as described above

## Metrics

The application makes metrics available at the `/metrics/` endpoint.
You can get metrics in JSON and Prometheus format:

```
# Get Metrics in JSON format
curl -H 'Accept: application/json' -X GET http://localhost:8080/metrics/ | json_pp

# Get Metrics in Prometheus format
curl -H 'Accept: text/plain' -X GET http://localhost:8080/metrics/
```

To configure Prometheus to scrape metrics from the application
add this to  `prometheus.yml` under `scrape_configs:`:

```
  - job_name: 'helidon'

    metrics_path: '/metrics/'
    # metrics_path defaults to '/metrics'
    # scheme defaults to 'http'.

    static_configs:
    - targets: ['localhost:8080']
```

Once Prometheus is runnining access the console (e.g. `localhost:9090/graph`)
 and click `Graph`. Enter `application:accessctr` then click
 `Execute`.

 You should see the graph plotting the application's `accessctr`
 metric. Exercise the application some more using the curl commands
 that were described earlier. Click `Execute` again and you should
 see the counter increase.

## Tracing

By default the application is configured to connect to zipkin at `http://localhost:9411`.
This is configured in `application.yaml`.

Start the Zipkin docker container:

```
docker run -d -p 9411:9411 openzipkin/zipkin
```

Then exercise the application:

```
curl -X POST -d '{"greeting" : "Howdy"}' http://localhost:8080/greet/slowgreeting
```
 
To view traces go to http://localhost:9411/zipkin/

Click on "Find a Trace". Click on "Find Traces" and sort by newest. 
You should see a roughly 2 second trace.


## Deploy the application to Kubernetes

```
kubectl cluster-info                # Verify which cluster
kubectl get pods                    # Verify connectivity to cluster
kubectl create -f target/app.yaml   # Deply application
kubectl get service quickstart-se  # Get service info
```
