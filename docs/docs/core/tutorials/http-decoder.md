---
id: http-decoder
filename: tutorials/http-decoder
nav-menu: core
layout: core
title: Form Validation Using HTTP Decoder
prev: core/tutorials/getting-started.html
next: core/tutorials/authorization-tutorial.html
redirect_from: "docs/index.html"
---
## Purpose
In this tutorial we will create an async servlet that will add contacts to the list, parse requests and process form 
validation with the help of `HttpDecoder`.

## To proceed with this guide you have 2 options:

* Download and run [working example](#working-example)
* Follow [step-by-step guide](#step-by-step-guide)

## Working Example

To run the example **in IDE**, you need to [clone DataKernel](https://github.com/softindex/datakernel.git) locally and
import it as a Maven project. Before running the example, build the project (**Ctrl + F9** for IntelliJ IDEA).

Then open **HttpDecoderExample** class, which is located at **datakernel -> examples -> tutorials -> decoder**
and run its *main()* method. Open your favourite browser and go to [localhost:8080](http://localhost:8080).

## Step-by-step guide
### 1. Set up project structure
First, create a folder for application and build an appropriate project structure:

{% highlight bash %}
decoder
└── pom.xml
└── src
    └── main
        └── java
            └── Address.java
            └── Contact.java
            └── ContactDAO.java
            └── ContactDAOImpl.java
            └── HttpDecoderExample.java
        └── resources
            └── static
                └── contactList.html
{% endhighlight %}


Configure your `pom.xml` file [in the following way](https://github.com/softindex/datakernel/blob/master/examples/tutorials/decoder/pom.xml)
to add a maven dependency to use DataKernel in your project.

### 2. Create needed components
Before moving to the main part of this tutorial which is creating **AsyncServlet** and using **HttpDecoder**,
our application also requires POJOs, DAOs and a single HTML page. These components
are pretty simple and don't include
DataKernel-specific features, so we won't describe them in details. You can find the needed sources here:

* [**Contact**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/decoder/src/main/java/Contact.java)
* [**Address**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/decoder/src/main/java/Address.java)
* [**ContactDAO**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/decoder/src/main/java/ContactDAO.java)
* [**ContactDAOImpl**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/decoder/src/main/java/ContactDAOImpl.java)
* [**contactList.html**](https://github.com/softindex/datakernel/blob/master/examples/tutorials/decoder/src/main/resources/static/contactList.html)

### 3. Create AsyncServlet

Consider this example as a concise presentation of MVC pattern:
* To model a **Contact** representation, we will create a plain java class with fields (name, age, address), constructor and accessors to the fields.
* To simplify example, we will use an **ArrayList** to store the **Contact** objects. **ContactDAO** interface and its implementation are used for this purpose.
* To build a view we will use a single html file, compiled with the help of the Mustache template engine.
* An **AsyncServlet** will be used as a controller. We will also add **RoutingServlet** to determine respond to a particular endpoint.
* **HttpDecoder** provides you with tools for parsing requests. So, here are two custom parsers which will be used for
validation - *addressDecoder* and *contactDecoder*:

{% highlight java %}
{% github_sample /softindex/datakernel/blob/master/examples/tutorials/decoder/src/main/java/HttpDecoderExample.java tag:REGION_1 %}
{% endhighlight %}

* Now these decoders can be used in our servlet:
{% highlight java %}
{% github_sample /softindex/datakernel/blob/master/examples/tutorials/decoder/src/main/java/HttpDecoderExample.java tag:REGION_2 %}
{% endhighlight %}

##### [See full example on GitHub](https://github.com/softindex/datakernel/blob/master/examples/tutorials/decoder/src/main/java/HttpDecoderExample.java).

* Here we provide an **AsyncServlet**, which receives **HttpRequest** from clients, creates **HttpResponse** depending on route path and sends it.
* *applyTemplate(Mustache mustache, Map<String, Object> scopes)* fills the provided Mustache template with given data.
* Inside the **RoutingServlet** two route paths are defined. First one matches requests to the root route `"/"` - 
it simply displays a contact list.
The second one, `"/add"` - is an HTTP `POST` method which adds or dismisses new users. We will process this request parsing with the 
help of aforementioned **HttpDecoder**, using *decode(request)* method:
{% highlight java %}
{% github_sample /softindex/datakernel/blob/master/examples/tutorials/decoder/src/main/java/HttpDecoderExample.java tag:REGION_3 %}
{% endhighlight %}
* **Either** represents a value of two possible data types (**Contact**, **DecodeErrors**). **Either** is either **Left** or **Right**.
We can check if **Either** contain only **Left**(**Contact**) or **Right**(**DecodeErrors**) using *isLeft* and *isRight* methods.

#### 4. Launch
Now you can launch the application. Run *HttpDecoderExample.main()* and then go to [localhost:8080](http://localhost:8080).