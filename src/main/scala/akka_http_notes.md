# Akka HTTP basics

---

Akka HTTP is
- a suite of libraries
- focused on HTTP integration of an application
- Designed for both services and clients
- based on Akka actors and Akka streams

Akka HTTP is NOT:
- a framework

Akka HTTP strengths
- stream-based, with backpressure for free down to the TCP level
- multiple API levels for control vs ease of use

Core concepts
- HttpRequest, HttpResponse
- HttpEntity - both the payload for requests and responses
- Marshalling - turn your data into an over the wire format such as JSON

---

# Akka HTTP server
Goal: receive HTTP requests, send HTTP responses
- synchronously via a function HttpRequest => HttpResponse
- async via a function HttpRequest => Future[HttpResponse
- async via streams, with a Flow[HttpReqeust, HttpResponse, _]

all of the above turn into flows sooner or later

Under the hood: 
- the server receives HttpRequests (transparently)
- the requests go through the flow we write
- the resulting responses are served back (transparently)