# shortforms-self-assessment

Backend for the Short Form Self Assessment journey. It generates the XML payload for SA1, CWF1, Cease trading from the frontend submission, sends it to iForms, and returns the acknowledgement reference and timestamp.

## Requirements

- Java and sbt

## Running locally

```
sbt run
```

The service listens on port 10080. It expects these upstream services by default:

| Service           | Default                 | Config key                                |
|-------------------|-------------------------|-------------------------------------------|
| auth              | `http://localhost:8500` | `microservice.services.auth`              |
| iForms submission | `http://localhost:9000` | `microservice.services.iforms-submission` |


## Testing

```
sbt test          # unit tests
sbt it/test       # integration tests
```

### License

This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).
