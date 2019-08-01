---
id: template-engine-integration
filename: tutorials/template-engine-integration
title: Simple web application with template engine integration
prev: core/tutorials/getting-started.html
next: core/tutorials/using-react.html
nav-menu: core
layout: core
---
## Purpose
In this guide you will learn how to implement template engines in DataKernel applications. You will create a Poll app which 
can create new polls with custom title, description and options. After poll is created, its unique link is generated. It 
leads to a page where you can vote.

## Introduction
Usage of template engines is a frequent practice, particularly when creating web applications, as it simplifies working 
with static HTML pages. See how simple it is to implement such features using DataKernel HTTP module. Your 
embedded application server will have only about **100 lines of code** with **no additional xml 
configurations**. In this example we will use **Mustache** as a template engine.

## What you will need:

* About 30 minutes
* Your favorite IDE
* JDK 1.8+
* Maven 3.0+

## Which DataKernel modules will be used:

* [HTTP](/docs/core/http.html)
* [Launchers](/docs/core/launcher.html)
* [Promise](/docs/core/promise.html)
* [ByteBuf](/docs/core/bytebuf.html)


## To proceed with this guide you have 2 options:

* Download and run [working example](#working-example)
* Follow [step-by-step guide](#step-by-step-guide)

## Working Example
[Clone DataKernel](https://github.com/softindex/datakernel.git) locally with IDE tools and import it 
as a Maven project. Before running the example, build the project (**Ctrl + F9** for IntelliJ IDEA).

Then, go to [Running the application](#running-the-application) section.

## Step-by-step guide
### 1. Set up project structure
First, create a folder for application and build the appropriate project structure:

{% highlight bash %}
template-engine
└── pom.xml
└── src
    └── main
        └── java
            └── PollDao.java
            └── PollDaoImpl.java
            └── ApplicationLauncher.java
        └── resources
            └── site
                └── listPolls.html
                └── singlePollCreate.html
                └── singlePollView.html
{% endhighlight %}

Next, configure your **pom.xml** file. We will need the following dependencies: *datakernel-http*, *datakernel-promise*, 
*datakernel-bytebuf*, *http-launcher*, *datakernel-boot* and *Mustache*. So, your **pom.xml** should 
[look like this](https://github.com/softindex/datakernel/blob/master/examples/tutorials/template-engine/pom.xml).

{% include note.html content=" we don't need to specify datakernel-promise and datakernel-bytebuf as they are transitive dependencies "%}

### 2. Create needed components
Before getting to the most interesting and important **ApplicationLauncher** class (starts our servlet, defines most of the logic, 
routing and generation of HTML pages), our application also requires POJOs, DAOs and several HTML pages. These components 
are pretty simple and don't include 
DataKernel-specific features, so we won't describe them in details. You can find the needed classes and HTML pages here:

Classes:

* [**PollDao**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/template-engine/src/main/java/PollDao.java) interface - describes basic functionality of our poll app (such as *find*, *update*, *remove* etc).
* [**PollDaoImpl**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/template-engine/src/main/java/PollDaoImpl.java) - implements **PollDao**.

HTML pages:
* [**listPolls**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/template-engine/src/main/resources/templates/listPolls.html) - displays all currently available polls.
* [**singlePollCreate**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/template-engine/src/main/resources/templates/singlePollCreate.html) - page for creating a new poll.
* [**singlePollView**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/template-engine/src/main/resources/templates/singlePollView.html) - page for voting.


After implementing all of these components, we can finally get to the launcher.

### 3. Create launcher
**ApplicationLauncher** launches our application and takes care of routing and generating needed content on HTML pages. We 
will extend DataKernel **HttpServerLauncher**, which takes care of application lifecycle:

{% highlight java %}
{% github_sample softindex/datakernel/blob/master/examples/tutorials/template-engine/src/main/java/ApplicationLauncher.java tag:EXAMPLE %}
{% endhighlight %}

{% include note.html content=" in this example we are omitting error handling to keep everything brief and simple" %}

Let's have a closer look at the launcher. 

* *applyTemplate(Mustache mustache, Map<String, Object> scopes)* fills the provided Mustache template with given data.
* *getBusinessLogicModules()* supplies our launcher with business logic providing **PollDaoImpl** and DataKernel **AsyncServlet**. 

In the **AsyncServlet** we create three Mustache objects for our three HTML pages. 
Then we create a DataKernel **RoutingServlet** and define routing. Routing approach resembles Express. For example, 
here's the request to the homepage:

{% highlight java %}
{% github_sample softindex/datakernel/blob/master/examples/tutorials/template-engine/src/main/java/ApplicationLauncher.java tag:REGION_1 %}
{% endhighlight %}

In this request we are getting all current polls and information about them. This information is used to generate *listPolls* page correctly. 


Method *map(@Nullable HttpMethod method, String path, AsyncServlet servlet)* adds the route to the **RoutingServlet**: 
 * *method* is one of the HTTP methods (`GET`, `POST`, and so on) 
 * *path* is the path on the server 
 * *servlet* defines the logic of request processing. If you need to get some data from the *request* while processing you can use:
     * *request.getPathParameter(String key)*/*request.getQueryParameter(String key)* ([see example of query parameter usage](https://github.com/softindex/datakernel/blob/master/examples/core/http/src/main/java/HttpRequestParametersExample.java) 
      to provide the key of the needed parameter and receive back a corresponding String
     * *request.getPostParameters()* to get a [Promise](http://datakernel.io/docs/core/promise.html) of Map of all request parameters


Let's also take a look at the next request:

{% highlight java %}
{% github_sample softindex/datakernel/blob/master/examples/tutorials/template-engine/src/main/java/ApplicationLauncher.java tag:REGION_2 %}
{% endhighlight %}

This request returns a page with specific poll (if there is a poll with such *id*). 
Pay attention to the provided path **"/poll/:id"**. `:` states that the following characters until the next `/` is a 
variable which keyword is, in this case, *id*. 

The next requests with **/create**, **/vote**, **/add** and **/delete** paths take care of providing page for creating 
new polls, voting, adding created polls to the *pollDao* and deleting them from the **PollDao** respectively.

Also, we defined *main()* method which will start our launcher.


And that's it, we have a full-functioning poll application!

## Running the application
Open **PollLauncher** class and run its *main()* method.
Then open your favourite browser and go to [localhost:8080](http://localhost:8080).