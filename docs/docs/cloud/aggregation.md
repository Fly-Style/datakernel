---
id: aggregation
filename: aggregation
title: LSM-Tree Aggregation Module
prev: cloud/ot.html
next: cloud/cube.html
nav-menu: cloud
layout: cloud
---

LSMT Aggregation represents database tables containing pre-aggregated data with possibility to define user-defined 
aggregate functions.

You can add Aggregation module to your project by inserting dependency in `pom.xml`: 

{% highlight xml %}
<dependency>
    <groupId>io.datakernel</groupId>
    <artifactId>datakernel-aggregation</artifactId>
    <version>{{site.datakernel_version}}</version>
</dependency>
{% endhighlight %}

## Features
* Log-Structured Merge Trees as core storage principle (unlike OLTP databases, it is designed from ground up for OLAP 
workload, so databases built on top of this table can easily handle high insert volumes of data, for example, 
transactional logs)
* Up to ~1.5M of inserts per second into aggregation on single core
* Aggregations storage medium can use any distributed file system

## This module on [GitHub repository](https://github.com/softindex/datakernel/tree/master/cloud-lsmt-aggregation)



