# Hierarchical Graph

A simple query engine for hierarchical graphs in a Scala script.

## What is a hierarchical graph?

A hierarchical graph is a directed graph augmented with a _tree-like
hierarchy_ among nodes. It consits of:

- __Leaf nodes__ and __edges__ between leaf nodes (dependencies).
- Tree-like hierarchies between __leaf nodes__ and __non-leaf nodes__ (ownership).

There are many hierarchical graphs in the real world:

- Managerial hierarchies and functional dependencies in a big organization
- Call graphs of computer programs

It is difficult to gain insights into a flat graph with too many edges and nodes.
Hierarchy is the key to make a complex graph understandable.

## Representing a hierarchical graph in text

A typical herarchical graph can be represented by a CSV file as follows:

    a.b.c,  x.y

In the above, the dot-separated string `a.b.c` (similarly "x.y") represents
the hierarchy of the nodes "a", "b" and "c":

- "a" owns "b"
- "b" owns "c"

The node "c" is a leaf node, while "a" and "b" are non-leaf nodes. Each line in
the CSV file represents a directed edge (dependency) in the graph.

Non-leaf nodes become dependent due to the depndencies of their leaf nodes.

## Querying a hierarchical graph

A particular question about hierarchical graphs is:

> Why a node `A` depends on another node `B`?

The question can be made more precisely using technical terms:

> What are the shortest paths from A to B, which only contain one-hop in A and
> one-hop in B?

This is a typical shortest path problem. The current engine implements an
algorithm to answer such queries.

## Play

Note: _please refer to the section [Run](#run) on how to build the program_.

``` bash
./hiergraph Hello.csv Hello java.io.File

./hiergraph Hello.csv Hello java.net

./hiergraph Hello.csv Hello java.util.regex

./hiergraph Hello.csv java.lang.String java.io
```

The file [Hello.csv](./Hello.csv) is generated using context-insensitive
analysis of the [doop framework](https://github.com/plast-lab/doop-mirror). The
program is the following:

```java
public class Hello {
  public static void main(String[] args) {
    new Hello().foo();
  }

  void foo() {
    System.out.println("hello, world!");
  }
}
```



## Usage

The program can be run in two modes:

- Batch mode: `hiergraph deps.csv -s -n 4 a.b.c p.q`
- REPL mode: `hiergraph deps.csv` and input `help` for commands

Options:

```
        -s        silent, do not print debug informaiton

        -n N      limit the number of path to N, default is 5
```

## Run

Prerequisite: install `scala-cli` (https://scala-cli.virtuslab.org/)

### Run as script

``` bash
scala-cli GraphQuery.scala -- Hello.csv                        # start the REPL

scala-cli GraphQuery.scala -- Hello.csv Hello java.util.regex  # batch job
```

### Run as an executable

First, generate an executable:

``` bash
scala-cli package --native -o hiergraph GraphQuery.scala
```

The option `--native` tells `scala-cli` to produce a native executable instead
of a packaged Java application launcher.

Now run the executable:

``` bash
./hiergraph Hello.csv                           # start the REPL

./hiergraph Hello.csv Hello java.util.regex     # batch job
```

## License

MIT