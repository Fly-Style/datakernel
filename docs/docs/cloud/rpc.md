---
id: rpc
filename: rpc
title: RPC Module
prev: core
next: cloud/fs.html
nav-menu: cloud
layout: cloud
---
RPC module allows to build distributed applications that require efficient client-server interconnections between servers.

You can add RPC module to your project by inserting dependency in `pom.xml`: 

{% highlight xml %}
<dependency>
    <groupId>io.datakernel</groupId>
    <artifactId>datakernel-rpc</artifactId>
    <version>{{site.datakernel_version}}</version>
</dependency>
{% endhighlight %}

* Ideal for creation of near-realtime (i.e. memcache-like) servers with application-specific business logic
* Up to ~5.7M of requests per second on single core
* Pluggable high-performance asynchronous binary RPC streaming protocol
* Consistent hashing and round-robin distribution strategies
* Fault tolerance - with re-connections to fallback and replica servers

shows a "Hello World" Remote-Procedure-Call client and and server interaction.

## Example
{% include note.html content="To run the example, you need to clone DataKernel from GitHub: 
<br> <b>$ git clone https://github.com/softindex/datakernel</b> 
<br> And import it as a Maven project. Before running the example, build the project. 
<br> The example is located at <b>datakernel -> examples -> cloud -> rpc</b>." %}

In the "Hello World" client and server **RPC Example** client sends a request which contains word "World" to server. When 
server receives it, it sends a respond which contains word "Hello ". If everything completes successfully, we get the 
following output:

{% highlight bash %}
Got result: Hello World
{% endhighlight %}

Let's have a look at the implementation:
{% highlight java %}
{% github_sample /softindex/datakernel/blob/master/examples/cloud/rpc/src/main/java/RpcExample.java tag:EXAMPLE %}
{% endhighlight %}

**RpcExample** class extends **Launcher** to help us manage application lifecycle.

We need to provide **RpcServer** and **RpcClient** with relevant configurations and required dependencies using 
DataKernel DI. **RpcClient** sends requests to the specified server according to the provided **RpcStrategies** 
(getting a single RPC-service). 
For **RpcServer** we define the type of messages which it will proceed, corresponding **RpcRequestHandler** and listen port.

Since we extend **Launcher**, we will also override 2 methods: *getModule* to provide [**ServiceGraphModule**](/docs/core/service-graph.html) 
and *run*, which represents the main logic of the example.

Finally, we define *main* method, which will launch our example.