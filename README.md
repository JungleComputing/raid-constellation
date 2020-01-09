# Resource Aware Inference Distribution - RAID
RAID is a dynamic resource management and scheduling system for inference task distribution on 
edge devices. It uses [Constellation](https://github.com/NLeSC/Constellation) for communication and scheduling, and TensorFlow Serving for applying ML models.

The system has three types of Agents, **source**, **predictor** and **target**. 

* The Source produces data, currently supports reading from the file system. It can be extended to come from an external input source, such as a camera.
* The Target collects the results and stores them in a log file, this can be extended to whatever functionality that is desired from the results.
* The Predictor will steal tasks from the source, perform the prediction and send the result to a specified target. 
Predictors will typically be run on the edge devices, but it can be used anywhere.

RAID supports context-aware execution, meaning that we can specify what type of tasks should be performed at what Predictor. This is done by using *labels* when starting up a source, only predictors with matching labels will steal this specific
task.

## <a name="requirements"></a> Requirements

#### Running
* Java JRE >= 11 ([Constellation](https://github.com/NLeSC/Constellation) supports Java 8, but since it is depricated we made sure RAID would run on Java 11).
* [TensorFlow Serving](#TensorFlowServing) installed on all devices where predictions will occur. Requires docker unless you wish to build the binary yourself.


#### Compiling
* All dependencies of RAID, compiled with __gradle__
* Java JDK >= 11


## Currently Supported Models

* mnist: MNIST DNN
* mnist_cnn: MNIST CNN, slightly larger model with better accuracy than MNIST DNN
* cifar10: CIFAR10 CNN
* yolo: YOLO v2 full model
* tiny_yolo: YOLO v2 smaller model

#### Extending With New Models
See README in `src/main/java/nl/zakarias/constellation/raid/`.

## <a name="installation"></a> Installation

In order to install everything and compile a distribution run the following in the root directory:

```bash
git clone https://github.com/ZakariasLaws/raid-constellation
cd raid-constellation
./gradlew installDist
```

This will create the distribution in `build/install/raid-constellation`.

#### Edge Devices
\- When installing on edge devices, only copy the distribution directory, the TensorFlow Serving config file and 
the desired models to the device. Make sure to maintain the same folder structure as if you installed everything with 
gradle.

RAID uses [TensorFlow Serving](#TensorFlowServing) to serve models and perform predictions. This binary needs to be 
manually installed on each device and the location must be provided during [Configuration](#Configuration). For AArch64
devices, you will most likely need to cross-compile it from source, 
[This Github Tool for TF Serving on Arm](https://github.com/emacski/tensorflow-serving-arm) might do the trick.

## <a name="configuration"></a> RAID Configuration 
#### <a name="environment_variable"></a> Environment Variables
For running this application, RAID requires the following environment variable to be set on *ALL* devices.

```bash
export RAID_DIR=/build/path/raid-constellation
export TENSORFLOW_SERVING_PORT=8000
export CONSTELLATION_PORT=4567
```

#### Set SSH Environment (remote startup script uses ssh, not strictly necessary)
To setup the SSH keys, see: [Setup SSH keys and copy ID](https://www.digitalocean.com/community/tutorials/how-to-set-up-ssh-keys--2)

In order to enable passing environment variables through SSH sessions on Linux, configure the `./ssh/environment` 
file as well as enable `PermitUserEnvironment` in the sshd configuration.
See [this StackExchange thread](https://superuser.com/questions/48783/how-can-i-pass-an-environment-variable-through-an-ssh-command)

#### <a name="TensorFlowServing"></a> TensorFlow Serving
The application uses [TensorFlow Serving](https://www.tensorflow.org/tfx/guide/serving) to support different TensorFlow ML models. When starting a Predictor with the `run.bash` script, the TensorFlow serving API will start on in the background and run on local host. The TensorFlow Model config file is located at `tensorflow/tensorflow_serving/ModelServerConfig.conf`, it only supports *absolute paths* and **must** therefore be modified with the device system paths, on each device.

TensorFlow serving can be run either from a binary, or more commonly using docker, the startup script supports both.
If using docker, type **docker** when creating the RAID config file. Make sure that the permissions are set to allow user
level docker commands on your system.

The TensorFlow Model config file should look something like this, (see [TensorFlow Serving Config File](https://www.tensorflow.org/tfx/serving/serving_config) for more options):
```conf
model_config_list {
  config {
    name: 'mnist'
    base_path: '/path/to/model/dir/mnist'
    model_platform: 'tensorflow'
  }
  config {
  ....
}
```

The output of the `tensorflow_model_serving` is stored in `tensorflow_model_serving.log` in the bin directory. If one or more agents in charge of prediction for some reason do not work during run time, view this log to see if the error is related to TensorFlow Serving.

#### <a name="Configuration"></a> RAID Configuration File
Each device running an agent must have a configuration file in the location pointed to by the environment variable `RAID_DIR` (see [Environment Variable](#configuration)). To create this configuration file, run the `./configuration/configure.sh` script from the root directory and answer the questions. 

It is also possible to manually create the config file by copy pasting the following into a file named `config.RAID`, 
located in the dir pointed to by the environment variable `RAID_DIR`. Replace the right side of the equal sign with your local path:

```conf
CONSTELLATION_PORT=4567
TENSORFLOW_BIN=/usr/bin/tensorflow_model_server
TENSORFLOW_SERVING_CONFIG=/home/username/raid-constellation/tensorflow/tensorflow_serving/ModelServerConfig.conf
```
**NOTE** that the `CONSTELLATION_PORT` number must be **identical** on all devices in order for them to connect to the
 server and communicate.

## <a name="running"></a> Running

To start up an agent, navigate to the bin directory and execute the `/bin/distributed/run.bash` scripts with the 
appropriate parameters for that agent (available agents are [here](#agents)). Upon starting up a new execution, 
always startup the agents in the following order:

1. Constellation Server
2. Target (in order to get Activity ID)
3. Source(s) and Predictor(s)

It is possible to add another _target_ during runtime, but this new target cannot receive classifications from images 
produced by an already running _source_. _Predictors_ however, can process images 
from newly added _sources_ and send results to any _target_ specified when starting up the _source_.

To start the server, type the following command `/bin/distributed/constellation-server`.
```bash
cd $RAID_DIR
$ ./bin/distributed/constellation-server
Ibis server running on 172.17.0.1/10.72.152.146-4567#8a.a0.ee.40.52.7d.00.00.8f.dd.4e.46.8e.a9.36.23~zaklaw01+22
List of Services:
    Central Registry service on virtual port 302
    Management service on virtual port 304
    Bootstrap service on virtual port 303
Known hubs now: 172.17.0.1/10.72.152.146-4567#8a.a0.ee.40.52.7d.00.00.8f.dd.4e.46.8e.a9.36.23~zaklaw01+22
```

When executing the server, we see the IP and the port number on which it listens, from the example above the 
`IP=10.72.152.146` and `port=4567`. The port is retrieved from the RAID configuration file 
(see [configuration](#configuration)) and the IP should be provided as the _second_ argument when starting any agent.

When starting one of the agents, the first, second and third argument follows the same pattern for all of them. 
The first argument (s/t/p) specifies whether it should run the _source_, _target_ or _predictor_ respectively, 
the second is the IP and the third is the _pool name_. The pool name can be any name, used by the server to distinguish 
each Constellation execution in the case of multiple simultaneous ones. 

```bash
./bin/distributed/run.bash <s/t/p> <IP> <Pool Name> [params]
```

#### Target
When starting the _target_, the ID of the activity collecting the results will be printed to the screen. Use this ID 
when starting up a _source_ agent.
```bash
./bin/distributed/run.bash t 10.72.152.146 test.pool.name

...
09:57:35,085 INFO  [CID:0:1] nl.zakarias.constellation.raid.collectActivities.CollectAndProcessEvents - In order to target this activity with classifications add the following as argument (exactly as printed) when initializing the new SOURCE: "0:1:0"
...
```

Possible parameters for the Target are:

* -outputFile /path/to/store/output/log
  * Each target produces a log file, storing the results of the predictions
* -profileOutput /path/to/store/profiling
  * Each target produces a gantt log file, which can be used to visualize the scheduling of jobs in Constellation.

The -profileOutput argument **MUST** be the last argument provided (if provided).

#### Predictor
Labels used here are A, B and C, meaning that this agent will only steal jobs having labels A, B or C.

```bash
./bin/distributed/run.bash p 10.72.152.146 test.pool.name -context A,B,C
```

possible parameters for Predictor are:
* -nrExecutors <number\>
  * Set the number of executors to use (each executor runs asynchronously on a separate thread)
* -context: Comma separated list of strings, containing at least one value, for example "label-1,test,2kb". The Predictor will only steal tasks with at least one matching label
  
Only two models are in this repository (mnist, mnist_cnn). Additional ones need to be added manually, as they can be large. Store new models in `/tensorflow/tensorflow_serving/models/` in the TensorFlow **SavedModel** format, 
see [TensorFlow SavedModel](https://www.tensorflow.org/beta/guide/saved_model), and update the TensorFlow Model Serving config file.
  
#### Source
The source requires the following arguments:
* -context: Comma separated list of strings, containing at least one value, for example "label-1,test,2kb". All submitted images will have _all_ of these labels, meaning they can be stolen by predictors with _one or more_ matching label.
* -target: The target activity identifier to send the result of the predictions to, printed to the screen when starting up a _target_ agent.
* -dataDir: The directory of where the data to be transmitted is stored
* -modelName: The type of model which should be used, see [Inference Models](#models) for availability
* -batchSize: The number of images to send in each task (default is 1)
* -endless: If set the source will keep submitting images forever, batchCount will be ignored (default is false)
* -batchCount: The number of batches to send in total before exiting, ignored if endless is set to true (default is 100)
* -timeInterval: The time to wait between submitting two batches, in milliseconds (default it 100)

```bash
./bin/distributed/run.bash s 10.72.152.146 test.pool.name -context A -target 0:1:0 -dataDir /home/username/MNIST_data/ -modelName mnist -batchSize 1
```

## Production
When executing in production, everything in the `log4j.properties` file should be set to false, and the command line 
arguments supplied when starting up a Constellation agent should be reviewed. It can be found in the `run.bash` file and has the following syntax `java -p ...`. Especially profiling (`-Dibis.constellation.profile=true`)can drastically slow down execution. 

For more Constellation specific arguments see [Constellation Configuration Javadoc](https://junglecomputing.github.io/Constellation/)
