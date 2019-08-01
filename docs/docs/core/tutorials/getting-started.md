---
id: getting-started
filename: tutorials/getting-started
nav-menu: core
layout: core
title: Getting Started
next: core/tutorials/http-decoder.html
redirect_from: "docs/index.html"
---
## Purpose
In this tutorial we will create a simple HTTP server which sends a “Hello World!” greeting. Using DataKernel
[Launchers](/docs/core/launcher.html), particularly `HttpServerLauncher`, you can 
write a full-functioning server in around 10 lines of code.

## What you will need:

* About 5-10 minutes
* Your favourite IDE
* JDK 1.8+
* Maven 3.0+

## To proceed with this guide you have 2 options:

* Download and run [working example](#1-working-example)
* Follow [step-by-step guide](#2-step-by-step-guide)

## 1. Working Example

To run the example **in IDE**, you need to [clone DataKernel](https://github.com/softindex/datakernel.git) locally and 
import it as a Maven project. Before running the example, build the project (**Ctrl + F9** for IntelliJ IDEA).

Then open **HttpHelloWorldExample** class, which is located at **datakernel -> examples -> tutorials -> getting-started** 
and run its *main()* method. Open your favourite browser and go to [localhost:8080](http://localhost:8080).

## 2. Step-by-step guide

First, create a folder for application and build an appropriate project structure:

{% highlight bash %}
getting-started
└── pom.xml
└── src
    └── main
        └── java
           └── HttpHelloWorldExample.java
{% endhighlight %}


Configure your `pom.xml` file [in the following way](https://github.com/softindex/datakernel/blob/master/examples/tutorials/getting-started/pom.xml) 
to add a maven dependency to use DataKernel in your project.

Then, write down the following code to `HttpHelloWorldExample.java`:
{% highlight java %}
{% github_sample /softindex/datakernel/blob/master/examples/tutorials/getting-started/src/main/java/HttpHelloWorldExample.java tag:EXAMPLE%}
{% endhighlight %}

First, we extend **HttpHelloWorldExample** from **HttpServerLauncher**, which will help to manage application lifecycle. 
Next, we provide an **AsyncServlet**, which receives **HttpRequest** from clients, creates **HttpResponse** and sends it. 

Override *AsyncServer.serve* using lambda. This method defines processing of received requests. As you can 
see, we are using [Promise](/docs/core/promise.html) here, creating a promise of **HttpResponse** with code 
200 and "Hello World!" body.

Finally, define *main* method to launch our server with *launch* method. This method launches server in the following 
steps: injects dependencies, starts application, runs it and finally stops it.

Let's now test the application. Run *HttpHelloWorldExample.main()*, then open your favourite browser and go to 
[localhost:8080](http://localhost:8080).
You will receive a `Hello World!` message proceeded by the server. Congratulations, you've just created your first 
DataKernel application!

## What's next?
To make DataKernel more developer-friendly, we've created dozens of tutorials and examples of different scales, 
representing most of the framework's capabilities. Click "Next" to get to the next tutorial. You can also explore our 
[docs](/docs/core/index.html) first.
