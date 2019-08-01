---
id: todo-list-tutorial
filename: core/tutorials/todo-list-tutorial
title: To-Do list app using React
prev: core/tutorials/using-react.html
nav-menu: core
layout: core
---
## Purpose
In this guide we will create a To-Do List app using DataKernel modules and React. You will learn how to integrate React 
in DataKernel project and how to simply manage routing using HTTP module.

## Introduction
In this tutorial we will use DataKernel **HttpServerLauncher** and **AsyncServlet** classes for setting up our 
[embedded application server](#3-create-launcher). With this approach, you can create servers with no XML configurations 
or third-party dependencies. Moreover, **HttpServerLauncher** will automatically take care of launching, running and 
stopping the application, you'll only need to provide launcher with servlets.

## What you will need:

* About 40-60 minutes
* Your favorite IDE
* JDK 1.8+
* Maven 3.0+
* Node.js 8.0+

## Which DataKernel modules will be used:

* [HTTP](/docs/core/http.html)
* [Launchers](/docs/core/launcher.html)
* [Promise](/docs/core/promise.html)
* [Eventloop](/docs/core/eventloop.html)
* [Codec](/docs/core/codec.html)


## To proceed with this guide you have 2 options:

* Download and run [working example](#working-example)
* Follow [step-by-step guide](#step-by-step-guide)

## Working Example
[Clone DataKernel](https://github.com/softindex/datakernel.git) locally with IDE tools and import it 
as a Maven project. Before running the example, build the project (**Ctrl + F9** for IntelliJ IDEA).

Then, run the following command in example's folder in terminal:
{% highlight bash %}
$ npm run-script build
{% endhighlight %}

Finally, go to [Running the application](#running-the-application) section.

## Step-by-step guide
### 1. Set up the project
First, create a folder for application and build the appropriate project structure:

{% highlight bash %}
todo-list-tutorial
└── pom.xml
└── src
    └── main
        └── java
            └── Record.java
            └── RecordDAO.java
            └── RecordImplDAO.java
            └── ApplicationLauncher.java
        └── resources
            └── src
                └── TodoApp.js
                └── TodoList.js
                └── TodoService.js
                
{% endhighlight %}


Next, configure your **pom.xml** file. We will need the following dependencies: *datakernel-http*, *http-launchers*, 
*datakernel-promise*, *datakernel-eventloop*. So your **pom.xml** should look [like this](https://github.com/softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/pom.xml).

{% include note.html content=" we don't need to specify datakernel-promise and datakernel-eventloop as it is a transitive dependency"%}


Next, run these lines in example's folder in terminal:
{% highlight bash %}
$ npx create-react-app todo-list-tutorial
$ cd todo-list-tutorial
$ npm run-script build
$ mv * full-path-to-your-project/todo-list-tutorial/src/main/resources/
{% endhighlight %}
This will generate the basis of our front-end React part and move all the files from the `todo-list-tutorial` 
directory to the `src->main->resources->src` directory.


Remove the following files from `src->main->resources->src` directory:
 * App.css
 * App.js
 * App.test.js
 * logo.svg
 * serviceWorker.js


Remove all generated code from **index.css** and **index.js** files.

### 2. Create needed components
Before getting to the most interesting and important part, **ApplicationLauncher**, our application also requires POJOs, DAOs, 
several JS and CSS files. These components are pretty simple and don't include 
DataKernel-specific features, so we won't describe them in details. You can find the needed components here:

POJOs:
* [**Plan**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/src/main/java/Plan.java) - contains String *text* and *isComplete* flag.
* [**Record**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/src/main/java/Record.java) - contains String *title* and a list of **Plan**s *plans*.

DAOs:
* [**RecordDAO**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/src/main/java/RecordDAO.java) interface - describes adding and deleting records, finding them by their id and returning a full list of available records. 
* [**RecordImplDAO**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/src/main/java/RecordImplDAO.java) - implements **RecordDAO**.

JavaScript:
* [**index.js**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/front/src/index.js) - index .js file.
* [**TodoApp.js**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/front/src/TodoApp.js) - handles most of the operations with records.
* [**TodoList.js**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/front/src/TodoList.js) - displays list of records.
* [**TodoService.js**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/front/src/TodoService.js) - processes requests and communicating with our DataKernel server.

CSS:
* [**index.css**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/front/src/index.css) - provides styles for our application.

After implementing all these components, we can finally get to the launcher.

### 3. Create launcher
**ApplicationLauncher** is the main class of the program. Besides launching the application, it also handles routing and 
most of the corresponding logic. We will use DataKernel **HttpServerLauncher** and extend it:
{% highlight java %}
{% github_sample /softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/src/main/java/ApplicationLauncher.java tag:EXAMPLE %}
{% endhighlight %}

Let's take a closer look at the launcher. It can add new record, get all available records, delete record by its id and 
also mark plans of particular record as completed or not.

So, first, we've defined codecs for our two entities: **Plan** and **Record**:
{% highlight java %}
{% github_sample /softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/src/main/java/ApplicationLauncher.java tag:REGION_1 %}
{% endhighlight %}
These codecs will help us to encode/decode **Plan** and **Record** from/to JSONs to communicate with **TodoService.js**.

Method *object* returns a new **StructuredCodec** and, in case of **Plan** and **Record** entities, requires the 
following parameters:
 * **TupleParser2** *constructor* - basically a constructor of your class with 2 parameters. There are 
 several predefined **TupleParser**s for up to 6 parameters.
 * **String** *field1* - the first field of the encoded/decoded class
 * **Function** *getter1* - getter of *field1*
 * **StructuredCodec** *codec1* - codec for *field1* (depends on the type of the field, for example, `STRING_CODEC`, `BOOLEAN_CODEC`)
 * **String** *field2* - another field of the class
 * **Function** *getter2* - getter of *field2*
 * **StructuredCodec** *codec1* - codec for *field2*

Next, provide **RecordDAO** 
and **AsyncServlet** for loading static content from `/build` directory and taking care of routing.

Routing in DataKernel HTTP module resembles Express approach. Method *map(@Nullable HttpMethod method, String path, AsyncServlet servlet)* 
adds routes to the **RoutingServlet**: 
 * *method* (optional) is one of the HTTP methods (`GET`, `POST` etc) 
 * *path* is the path on the server 
 * *servlet* defines the logic of request processing. If you need to get some data from the *request* while processing you can use:
    * *request.getPathParameter(String key)*/*request.getQueryParameter(String key)* ([see example of query parameter usage](https://github.com/softindex/datakernel/blob/master/examples/core/http/src/main/java/HttpRequestParametersExample.java)) 
     to provide the key of the needed parameter and receive back a corresponding String
    * *request.getPostParameters()* to get a [Promise](http://datakernel.io/docs/core/promise.html) of Map of all request parameters

Pay attention to the requests with `:`, for example:
{% highlight java %}
{% github_sample /softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/src/main/java/ApplicationLauncher.java tag:REGION_3 %}
{% endhighlight %}
`:` states that the following characters until the next `/` is a variable whose keyword, in this case, is *recordId*. 

Also, take a look at the first request:
{% highlight java %}
{% github_sample /softindex/datakernel/blob/master/examples/tutorials/advanced-react-integration/src/main/java/ApplicationLauncher.java tag:REGION_2 %}
{% endhighlight %}
`*` states, that whichever path until the next `/` is received, it will be processed by our static servlet, which uploads 
static content from `/build` directory.

Finally, there is `main()` method which will start our launcher.

## Running the application
Open **ApplicationLauncher** and run its *main* method. Then open your favourite browser and go to 
[localhost:8080](http://localhost:8080).
Try to add and delete some tasks or mark them as completed.