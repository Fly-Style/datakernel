---
id: authorization-tutorial
filename: tutorials/authorization-tutorial
title: Simple app with authorization and session management
prev: core/tutorials/getting-started.html
next: core/tutorials/template-engine-integration.html
nav-menu: core
layout: core
---
## Purpose
In this guide you will learn how to create a simple authorization app with **log in**/**sign up** scenarios and session management. 

## Introduction
DataKernel doesn't include built-in authorization modules or solutions, as this process may significantly vary depending 
on project's business logic. This tutorial will provide you with a simple "best practice" example which you 
can extend and modify depending on your needs.

In this tutorial you will create a [server](#3-create-launcher) using DataKernel **HttpServerLauncher** and 
**AsyncServlet**. This approach allows to create embedded application server in about 100 lines of code with no 
additional XML configurations or third-party dependencies.

## What you will need:

* About 20 minutes
* Your favorite IDE
* JDK 1.8+
* Maven 3.0+

## Which modules will be used:

* [HTTP](/docs/core/http.html)
* [Launchers](/docs/core/launcher.html)
* [Promise](/docs/core/promise.html)


## To proceed with this guide you have 2 options:

* Download and run [working example](#working-example)
* Follow [step-by-step guide](#step-by-step-guide)

## Working Example
[Clone DataKernel](https://github.com/softindex/datakernel.git) locally with IDE tools and import it 
as a Maven project. Before running the example, build the project (**Ctrl + F9** for IntelliJ IDEA).

Then, go to [Running the application](#running-the-application) section.

## Step-by-step guide
### 1. Set up the project
Create a folder for application and build the following project structure:

{% highlight bash %}
auth
└── pom.xml
└── src
    └── main
        └── java
            └── AuthLauncher.java
            └── AuthService.java
            └── AuthServiceImpl.java
        └── resources
            └── site
                └── errorPage.html
                └── index.html
                └── login.html
                └── signup.html
{% endhighlight %}

Next, configure your **pom.xml** file. We will need the following dependencies: *datakernel-http*, *http-launchers*, 
*datakernel-promise*. So your **pom.xml** should look [like this](https://github.com/softindex/datakernel/blob/master/examples/tutorials/auth/pom.xml).

{% include note.html content=" we don't need to specify datakernel-promise as it is a transitive dependency" %}

### 2. Create needed components
Before getting to the most interesting and important part, **AuthLauncher**, our application also requires classes with 
authorization business logic and several HTML pages. These components are pretty simple and don't include 
DataKernel-specific features, so we won't describe them in details. You can find the needed classes and HTML pages here:

Classes:

* [**AuthService**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/auth/src/main/java/AuthService.java) interface - 
describes *authorize* and *register* methods
* [**AuthServiceImpl**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/auth/src/main/java/AuthServiceImpl.java) - 
simple implementation of **AuthService**. Feel free to extend it in accordance to your needs.

HTML pages:
* [**index**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/auth/src/main/resources/site/index.html) - displays all currently available polls
* [**login**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/auth/src/main/resources/site/login.html) - page for creating a new poll
* [**signup**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/auth/src/main/resources/site/signup.html) - page for voting
* [**errorPage**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/auth/src/main/resources/site/errorPage.html) - error page


After implementing all these components, we can finally get to the launcher.

### 3. Create launcher
Let's create an **AuthLauncher**, which is the main part of the application as it manages application lifecycle, routing 
and authorization processes. We will use DataKernel **HttpServerLauncher** and extend it:
{% highlight java %}
{% github_sample softindex/datakernel/blob/master/examples/tutorials/auth/src/main/java/AuthLauncher.java tag:EXAMPLE %}
{% endhighlight %}

Provide the following objects:
* **AuthService** - authorization and register logic 
* **Executor** - needed for StaticLoader
* **StaticLoader** - loads static content from `/root` directory
* **SessionStore** - handy storage for information about sessions
* **AsyncServlet** *servlet* - the main servlet which combines public and private servlets (for authorized and 
unauthorized sessions). As you can see, due to DI, this servlet only requires two servlets without their own dependencies
* **AsyncServlet** *publicServlet* - manages authorized sessions
* **AsyncServlet** *privateServlet* - manages unauthorized sessions

Let's take a closer look at how we set up routing for servlets. DataKernel approach resembles Express. For example, 
here's the request to the homepage for unauthorized users:
{% highlight java %}
{% github_sample softindex/datakernel/blob/master/examples/tutorials/auth/src/main/java/AuthLauncher.java tag:REGION_1 %}
{% endhighlight %}

Method *map(@Nullable HttpMethod method, String path, AsyncServlet servlet)* adds the route to the **RoutingServlet**: 
 * *method* (optional) is one of the HTTP methods (`GET`, `POST` etc) 
 * *path* is the path on the server 
 * *servlet* defines the logic of request processing. If you need to get some data from the *request* while processing you can use:
    * *request.getPathParameter(String key)*/*request.getQueryParameter(String key)* ([see example of query parameter usage](/docs/core/http.html#request-parameters-example)) 
  to provide the key of the needed parameter and receive back a corresponding String
    * *request.getPostParameters()* to get a [Promise](/docs/core/promise.html) of Map of all request parameters

Let's move on to the next requests. `GET` requests with paths **"/login"** and **"/signup"** upload the needed HTML pages.
`POST` requests with paths **"/login"** and **"/signup"** take care of log in and sign up logic respectively. 

Pay attention at `POST` **"/login"** rout. *serveFirstSuccessful* takes two servlets and waits until one of them 
finishes processing successfully. So if authorization fails, a Promise of **null** will be returned (**AsyncServlet.NEXT**), 
which means fail. In this case, simple **StaticServlet** will be created to load the *errorPage*. Successful log in will 
generate a session *id* for user, save string `"My saved object in session"` to browser cookies and also redirect user 
to **"/members"**.

Now let's get to the next servlet which handles authorized sessions. First, it redirects requests from homepage to **"/members"**: 
{% highlight java %}
{% github_sample softindex/datakernel/blob/master/examples/tutorials/auth/src/main/java/AuthLauncher.java tag:REGION_2 %}
{% endhighlight %}

Next, it takes care of all of the requests that go after **"/members"** path:
{% highlight java %}
{% github_sample softindex/datakernel/blob/master/examples/tutorials/auth/src/main/java/AuthLauncher.java tag:REGION_3 %}
{% endhighlight %}

Pay attention to the path **"/members/*"**. `*` states, that whichever path until the next `/` goes after **"/members/"**, 
it will be processed by this servlet. So, for example this route:
{% highlight java %}
{% github_sample softindex/datakernel/blob/master/examples/tutorials/auth/src/main/java/AuthLauncher.java tag:REGION_4 %}
{% endhighlight %}
is GET request for **"/members/cookie"** path. This request shows all cookies stored in the session.

**"/members/logout"** logs user out, deletes all cookies related to this session and redirects user to homepage.

Finally, we define `main()` method, which will start our launcher.

## Running the application 
Open `AuthLauncher` class and run its *main()* method.
Then open your favourite browser and go to [localhost:8080](http://localhost:8080). Try to sign up and then log in. When 
logged in, check out your saved cookies for session. You will see the following content: `My saved object in session`. 
Finally, try to log out. You can also try to log in with invalid login or password. 