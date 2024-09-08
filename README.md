*This file serves as a template for the project documentation of DSTI Data Pipeline Part 2 module*

# Option 1 - Project Covid Analysis 

## Objectives
1. Using [brazil_covid19_cities.csv](https://www.kaggle.com/datasets/unanimad/corona-virus-brazil?select=brazil_covid19_cities.csv) as source, compute a structurally equivalent file to [brazil_covid19.csv](https://www.kaggle.com/datasets/unanimad/corona-virus-brazil?select=brazil_covid19.csv).
2. Create a report comparing [brazil_covid19.csv](https://www.kaggle.com/datasets/unanimad/corona-virus-brazil?select=brazil_covid19.csv) to the newly generated file.
3. Execute the batch locally as standalone or in a spark cluster, and online in an AWS EMR cluster.

The design and strategy are described in the [docs/](docs) directory.

## Setup

### Requirements
- Java JDK version 11.x (11.0.4 or higher recommended): [Java SE 11 Archive Downloads](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html)
- Scala version 2.x (2.12.15 or higher recommended): [scala-lang.org/download/](https://www.scala-lang.org/download/)
- Apache Spark must be installed: [Download Apache Spark](https://spark.apache.org/downloads.html)
- `mill` build tool: [Installation instructions](https://com-lihaoyi.github.io/mill/mill/Intro_to_Mill.html#_installation)
- An account with [Amazon Web Services](https://aws.amazon.com/)
- The [AWS CLI](https://aws.amazon.com/cli/) must be configured

### Installation
1. Clone this repository and prepare the `./data/in` directory

```bash
# After installing mill, clone this repository
git clone https://github.com/thanhvo-uparis/dsti_data_pipeline2

# Prepare data input directory
cd dsti-spark-aggregator
mkdir data/in
```

2. Download the datasets from Kaggle, either via your browser or via the [Kaggle API](https://github.com/Kaggle/kaggle-api):

    - [brazil_covid19.csv](https://www.kaggle.com/datasets/unanimad/corona-virus-brazil?select=brazil_covid19.csv)
    - [brazil_covid19_cities.csv](https://www.kaggle.com/datasets/unanimad/corona-virus-brazil?select=brazil_covid19_cities.csv)

Place them both in `./data/in`.

## Run
All the below commands must be executed in the root directory `path/dsti-spark-aggregator/`:

### Local Standalone

```bash
# Aggregate city-level data into state-level data 
./mill tracker.standalone.run agg ./data/in/brazil_covid19_cities.csv new_brazil_covid19.csv

# Compare the newly created aggregation with the aggregate provided on Kaggle.com
./mill tracker.standalone.run cmp ./data/in/brazil_covid19.csv .data/out/new_brazil_covid19.csv
```

### Local Spark Cluster
```bash
# Assemble the jar for this project
./mill tracker.assembly 

# Aggregate city-level data to state-level data on local cluster
spark-submit --class TrackerCli ./out/tracker/assembly.dest/out.jar agg ./data/in/brazil_covid19_cities.csv new_brazil_covid19.csv

# Compare newly created file with provided file on local cluster
spark-submit --class TrackerCli ./out/tracker/assembly.dest/out.jar cmp ./data/in/brazil_covid19.csv ./data/out/new_brazil_covid19.csv

```

### Amazon Web Services
- See the [dedicated instructions](./aws) to execute the batch on AWS.