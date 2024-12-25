# A minimalist implementation of raft

A Raft implementation inspired by XnnYygn.

## Overview

I wanted to create something with the Netty framework and discovered the Raft consensus algorithm. Raft is a fascinating concept and, as the first distributed consensus algorithm I learned, I was motivated to implement its features after reading the original paper.

### Features

This project implements several key aspects of the Raft algorithm, including:

- **Leader Election**: Ensures that one server acts as the leader to manage the log replication process.
- **Log Replication**: Guarantees that logs are consistently replicated across all servers.
- **Membership Change**: Supports changes to the cluster membership (e.g., adding or removing a server).
- **Log Compaction**: Optimizes storage by compacting logs while maintaining the necessary state.

All of these features are encapsulated in the `xraft-core` module.

### Client Interaction

I believe that client interaction with the Raft implementation should be a service built on top of `raft-core`. To that end, I have created a simple key-value store called `raft-state-machine`. This store supports basic operations such as:

- **GET**: Retrieve the value associated with a specific key.
- **SET**: Store a value associated with a specific key.

## Getting Started

To get started with Xraft, you can clone the repository and explore the modules. Detailed instructions on how to run the key-value store and interact with the Raft protocol will be provided in the documentation.

