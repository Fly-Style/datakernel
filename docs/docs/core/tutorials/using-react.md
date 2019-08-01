---
id: using-react
filename: tutorials/using-react
title: Using React in DataKernel projects
nav-menu: core
layout: core
prev: core/tutorials/template-engine-integration.html
next: core/tutorials/todo-list-tutorial.html
---
## Purpose
In this guide you will learn how to integrate React in DataKernel projects.

## Introduction
You will use DataKernel **HttpServerLauncher** and **AsyncServlet** to set up the [server](#2-create-launcher), 
which will process requests. DataKernel makes this process extremely simple, so that you can complete the whole 
application in about 10 minutes.

## What you will need:

* About 10 minutes
* Your favorite IDE
* JDK 1.8+
* Maven 3.0+
* Node.js 8.0+

## Which DataKernel modules will be used:

* [HTTP](/docs/core/http.html)
* [Launchers](/docs/core/launcher.html)
* [Eventloop](/docs/core/eventloop.html)


## To proceed with this guide you have 2 options:

* Download and run [working example](#working-example)
* Follow [step-by-step guide](#step-by-step-guide)

## Working Example
[Clone DataKernel](https://github.com/softindex/datakernel.git) locally with IDE tools and import it 
as a Maven project. Before running the example, build the project (**Ctrl + F9** for IntelliJ IDEA).

Run the following command in example's folder in terminal:
{% highlight bash %}
$ npm run-script build
{% endhighlight %}

Then, go to [Running the application](#running-the-application) section.

## Step-by-step guide
### 1. Set up the project
First, create a folder for application and build an appropriate project structure:

{% highlight bash %}
react-getting-started
└── pom.xml
└── src
    └── main
        └── java
            └── io
                └── datakernel
                    └── examples
                        └── SimpleApplicationLauncher.java
{% endhighlight %}

Next, configure your **pom.xml** file. We will need the following dependencies: *datakernel-http*, *http-launchers*, 
*datakernel-eventloop*. So your **pom.xml** should look [like this](https://github.com/softindex/datakernel/blob/master/examples/tutorials/react-integration/pom.xml).

{% include note.html content=" we don't need to specify datakernel-eventloop as it is a transitive dependency"%}

Now let's add React to our project. Enter the following command in console in the project's root folder:

{% highlight bash %}
$ npx create-react-app react-integration
$ cd react-integration/
$ npm run-script build
$ mv *  full-path-to-your-project/react-integration/src/main/resources/
{% endhighlight %}
As a result, all the files generated in the `react-getting-started` directory will be moved to the 
`src->main->resources->src` directory. And that's it, we are done with React part.

### 2. Create launcher
Write down **SimpleApplicationLauncher**. It extends DataKernel **HttpServerLauncher**:
{% highlight java %}
{% github_sample softindex/datakernel/blob/master/examples/tutorials/react-integration/src/main/java/SimpleApplicationLauncher.java tag:EXAMPLE %}
{% endhighlight %}

**HttpServerLauncher** takes care of setting up all the needed configurations for HTTP server.

Provide **AsyncServlet**, which will open the **index.html** of the provided path.

Then write down *main* method, which will launch **SimpleApplicationLauncher**.
And that's it, no additional configurations are required.

Congratulations! You've just created a simple DataKernel project with React.

## Running the application
Open `SimpleApplicationLauncher` class and run its *main()* method.
Then open your favourite browser and go to [localhost:8080](http://localhost:8080).
