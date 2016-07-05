# PreX

**PreX** is an exception model that defies current exception model preconceptions. This repository contains its first implementation. This README gives an overview of the model and its implementation.

**What if you could act on an exception before it happens?**

This is the base premise of PreX -- the Preventive Exception Handling model. Instead of reacting to an exception -- often too late for practical purposes --, this new model offers proactive behaviour and allows you, as a developer, to write code to be run when an exception _might_ happen. What if instead of having a `TimeoutException` you could just slow down the rate of operations when the system detected that you were overloading a database? PreX enables this, and in this repository you'll find its first implementation.

Table of Contents
=================

  * [PreX](#prex)
  * [Main concepts of the PreX model](#main-concepts-of-the-prex-model)
    * [try-prevent-catch](#try-prevent-catch)
    * [sample](#sample)
    * [no_alarm](#no_alarm)
    * [Architecture (How does it work?)](#architecture-how-does-it-work)
  * [The first PreX implementation (this repository)](#the-first-prex-implementation-this-repository)
    * [Coordinator](#coordinator)
    * [Administration Application](#administration-application)
     * [Client library](#client-library)
     * [How to create a Probe](#how-to-create-a-probe)
     * [How to use the try-prevent-catch in the real-world](#how-to-use-the-try-prevent-catch-in-the-real-world)

As a model, PreX modifies the current `try-catch`semantics onto new, `try-prevent-catch`semantics. The system predicts that an exception can happen in the near future. This prediction is said to _**trigger**_ an _**alarm**_. The alarm interrupts the code within a `try` block and jumps to the `prevent` block, where the developer can specify any code he/she wishes. Once the `prevent` block is terminated, execution resumes as normal.

In practice, code that looked like this:
```
try {
   // ... some code ...
} catch ( TimeoutException e ) {
   // ... some exception handling code ...
}
```

Can turn into code that looks like this (some parameters will be explained later):

```
try ( <prediction context> ) {
   // ... some code ...
} prevent ( TimeoutException, predictionInformationObject ) {
   // ... some exception prevention code ...
} catch ( TimeoutException e ) {
// ... some exception handling code ...
}
```

# Main concepts of the PreX model
PreX, as a model, implements several important features that would ideally be implemented natively in languages. This Github projects offer an implementation of PreX as a Java library, with some slight deviations from the original model. In this section, we introduce the main concepts of PreX as a model. You can see [this section](#the-first-prex-implementation-this-repository) for implementation details.

## try-prevent-catch

PreX extends the idea of the `try-catch`construct with a new construct called the `try-prevent-catch` construct. The try block is modified to include a **prediction context**. The prediction context should be different for each piece of code and acts as an easy way to differentiate pieces of code. Thus, the syntax of try becomes `try ( <prediction context> ) { ... }`, where the _prediction context_ is a string.

With PreX, you have the ability to act on a _potential_ exception, before it _potentially_ happens. When the system predicts that an exception _might_ happen, it _triggers_ an _alarm_, which moves execution from within the _try_ block and into the _prevent_ block and, afterwards, back into the _try_ block (i.e. the _prevent_ block does not behave as a _catch_ block, terminating execution). The _prevent block_ gives the developer a **prediction information object** which can contain additional information, such as how much time you have left until the exception _potentially_ happens. Within this _prevent_ block, you can write any code you wish, but the goal is that some preventive action is taken, such as slowing down the rate of resources, cleaning machine cache or memory, or even allocating new resources. In the event that an exception still does happen, the _catch_ block can be written as normal. Picking an academic example, you can now write code such as the following (note that, as detailed in [this section](#the-first-prex-implementation-this-repository), the code in this implementation is not native and requires a library with slightly different syntax):

```
DBConnection db = getConnection();
ArrayList<WriteOperation> opers = getWriteOperations();

try ( <prediction context> ) {
  for (WriteOperation oper : opers)
    db.doOperation(oper);
} prevent ( TimeoutException, predictionInformationObject ) {
   // Alarm has been triggered! Slow down rate of execution!
   db.waitForAWhile();
} catch ( TimeoutException e ) {
  error("Connection has been lost!");
  e.printStackTrace();
}
```

## sample

PreX implementations use a machine learning system to predict exceptions. It is useful to provide this system with data (i.e. *features*) from within your application which it would otherwise have no way of finding out. For example, you might want to tell it how much progress has been made in the current operation, how large is the application-specific cache, or how many IO-heavy threads you currently have running. You do this by using a new keyword: `sample(<feature name>, <value>)`. For example, you can tell the prediction system how many operations are left in some application as such: `sample("operations left", getOpersLeft())`.

## no_alarm

It may happen that you do not want to interrupt your current work within the `try` block. Perhaps you're carrying out an important atomic activity which should not be disturbed by an `alarm` (the possibility of an exception), which would move execution temporarily to the `prevent` block. You can wrap any code which you don't want to be interrupted by PreX in within a `no_alarm-block`. For example:

```
try ( "some context" ) {
    // ... some code
    no_alarm {
      /// No alarms here! No way to get into the prevent block while here!
    }
} prevent ( ... ) {
  ...
} catch ( ... ) {
  ...
}
```

## Architecture (How does it work?)

PreX has a distributed architecture in its nature. Applications act as _code entities_ (with the _try-prevent-catch_ construct) or _probes_ (which gather system data), which communicate with a **coordinator** The coordinator is responsible for training prediction models and making predictions. It's where the data from the `sample` keyword ends, it's where prediction models are trained, it's where alarms are triggered from and it's generally where the magic happens.

The coordinator is a server that listens to incoming data. This data can come from **probes**, which gather whatever variables you wish ([here](https://github.com/Jorl17/prex-probe) is a cross-platform probe which is compatible with this implementation of PreX). These probes can the amount of free memory or the amount of concurrent connections to your companie's custom data warehousing solution. It's up to you. In addition, the coordinator uses data from the `sample` keyword, and data sent by the exception mechanism (e.g. information that an exception happened at time _t_, etc). You can then use an administration application to ask the coordinator to _train_ models for some exception (within some prediction context (remember every _try_ block gets a prediction context associated to it!). The coordinator will train models, determine the best, and start _triggering alarms_, thus "magically" making your code enter the prevent block.

# The first PreX implementation (this repository)

This is the first implementation of PreX. It is not implemented natively, so that you don't need a new JVM for your projects. All you have to do is setup a coordinator (included in this code), include a library in your project, setup some probes and use the administration application. You use the library to write code that _emulates_ the `try-prevent-catch` blocks detailed in the [model](#try-prevent-catch).

The project in this repository includes the following three components:

- The [coordinator](#architecture-how-does-it-work)
- An administration application to talk with the coordinator
- The Java library used to write your `try-prevent-catch` blocks or custom probes

There is already a PreX compatible probe that samples dozens of system-related variables. You can find it [here](https://github.com/Jorl17/prex-probe).

All three of these projects can be imported as an IntelliJ Idea IDE project. It is already setup to produce working jars/artifacts

## Coordinator

The coordinator listens on port 1610. At the moment, it only has one argument, which tells it if it should enable "predictions" or not. When you are gathering data and already have a trained model, you might still want to avoid using it, so you don't influence your results. In that case, you tell the coordinator to only gather data. Thus, you can launch the coordinator as:

``java -jar coordinator.jar predict``

or

``java -jar coordinator.jar no-predict``

The latter disables predictions. You will generally start with the latter form while gathering exception data (and using the [administration application](#administration-application) to mark individual _test runs_) and, then, move to the former.

## Administration Application

There are actually two applications bundled in this code. One of them offers a Swing-based GUI and the other offers command-line options. You can check the command line options with the `-h`, but you should probably get acquainted with the GUI first, as it will ease the concepts. Evidently, the coordinator has to be running when you connect the administration application (what would it talk to anyway?)

Within the administration application, you can:
- **Start the training for some _prediction context_**. The training might take a while, depending on the data. If you launched the coordinator with the `predict` argument, then the new model should instantly start being used
- **Add or remove features from a given _prediction context_**. A _prediction context_ not only uniquely identifies a block of code, but it is also used to map relevant features used for prediction. By default, it uses all features in the system to predict an exception, but you might know better and eliminate some of them if you wish.
- **Mark a _test run_ as started or stopped**. During training, you will need to isolate individual runs of your application. You can use one large _run_, simply using the administration application to start it, let it run and then stop it to train. However, if you are doing some custom setup such as artificially overloading your machines, you might want to isolate individual _runs_ with the application for better predictions.

## Client library

The client library is used in three ways:

- To implement probes which feed data to the coordinator
- To implement `try-prevent-catch` blocks in your code
- (Optionally) To implement your own administration client

All three of these simple rely on creating instances of the `PrexClient` class and invoking methods.

### How to create a Probe

If you want to create your own probe all you have to do is instantiate a `PrexClient` (it will automatically forge a connection with the coordinator -- so make sure it is running before!) and use the `sample` method to provide it with a `<variable name>,<variable value>` pair. The first argument of the `PrexClient` class should be a unique identifier (each probe should have its own). The third argument of the `PrexClient` constructor specifies a _buffer size_ before data is flushed to the coordinator. You should fine tune it for your network. An example of a simple code for a probe is the following (note that the buffer size is set to 30 samples):

```
PrexClient client = new PrexClient("probe1" /* unique id */, "localhost" /* coordinator host */, 1610 /* port */, 3 /* buffer size */);
while ( shouldContinue() )
   for ( Feature f : getAllFeatures() )
      client.sample(f.name(), f.value());
```

The above code shows how all you really have to do is call the `sample` method to make your own probes.

### How to use the `try-prevent-catch` in the real-world

The `try-prevent-catch` mechanism described [in the model](#try-prevent-catch) is implemented slightly differently in this repository. It uses Java generics and reflection to emulate syntactic additions to the language. Unfortunately, since it is only a library, there are some limitations. Most notably, you are forced to manually call a method whenever you want to check if an _alarm_ has been _triggered_. In other words, you'll have to call that method to be able to jump into the `prevent` block. 

An example of the library is shown below. Note that the use of lambda functions is optional, and they are graphically formatted to look more like a native syntactic addition to the language.

```
// Note that we leave the buffer size as is (best not to touch it unless we use the sample keyword a lot)
PrexClient c = new PrexClient("client1" /* unique id */, "localhost" /* coordinator host */, 1610 /* port */);

c
.Try ( "prediction context 1", (t) -> {
   // ... some code that might throw a SQLDataException ...
   
   // Must call this at least once! Otherwise there is no way to
   // enter the prevent block!
   t.check();
   
   // ... some code that might throw a SQLDataException ...
})
.Prevent( SQLDataException.class, ( predictionInformationObject ) -> {
  // ... some code to be run when alarms are triggered. Should only
  //     get here when a SQLDataException is predicted to possibly
  //     happen
  
.Catch( SQLDataException.class, ( exception ) -> {
  // ... Your regular "catch" code for the SQLDataException
}).sync(); // Note the call to sync!
```

The previous example highlights the following:
- You write the `try-prevent-catch` code by chaining calls after a `Try()` call
- You _can_ use lambda functions to make the code syntactically similar to a native implementation
- You must append the `.class` suffix to exception "declarations" when using the mechanism
- You must call `sync()` to set the process in motion

That's it! There are other variants of the code not covered in this README, but the above could should be enough of a basis for you to implement your own preventive code. Note that by using the library to write your `try-prevent-catch` blocks, it already sends data to the coordinator whenever an exception happens.

Remember that the full process for using this implementation of PreX involves:

1. Starting the coordinator
2. Starting probes
3. Marking the start of a run with an Administrator Application
4. Gathering data (you must have already written `Try-, letting exceptions happen, etc
5. Marking the stop of a run with an Administration Application (repeat steps 3.-5. as many times as necessary)
6. Asking the coordinator to train models (using the Administrator Application)
