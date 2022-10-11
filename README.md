# Hierarchical Graph

A simple query engine for hierarchical graph in a Scala script.

## What is a hierarchical graph?

A hierarchical graph is a directed graph augmented with a _tree-like
hierarchy_ among nodes. It consits of:

- __Leaf nodes__ and __edges__ between leaf nodes (dependencies).
- Tree-like hierarchies between __leaf nodes__ and __non-leaf nodes__ (ownership).

There are many hierarchical graphs in the real world:

- Managerial hierarchies and functional dependencies in a big organization
- Call graphs of computer programs

It is difficult to get insights to a flat graph with too many edges and nodes.
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

A particular question about hierarchical graph is:

> Why a node `A` depends on another node `B`?

The question can be made more precisely using technical terms:

> What are the shortest paths from A to B, which only contain one-hop in A and
> one-hop in B?

This is a typical shortest path problem. The current engine implements an
algorithm to answer such queries.

## Usage

The program can be run in two modes:

- Batch mode: `<program> deps.csv -s -n 4 a.b.c p.q`
- REPL mode: `<program> deps.csv` and input `help` for commands

See the section [below](#run) for `<program>`.

Options:

```
        -s        silent, do not print debug informaiton

        -n N      limit the number of path to N, default is 1""".stripMargin
```

## Run

Prerequisite: install `scala-cli` (https://scala-cli.virtuslab.org/)

### Run as script

```
scala-cli GraphQuery.scala -- deps.csv
```

### Run as a native executable

First, generate an executable:

```
scala-cli package --native -o hiergraph GraphQuery.scala
```

Now run the executable:

```
./hiergraph deps.csv
```

## License

MIT