# Java Redis

A lightweight Redis-compatible in-memory key-value server built from scratch in Java.

## Overview

This project is a custom implementation of a Redis-style server developed to understand how an in-memory data store works internally.

The project focuses on networking, command processing, data storage, protocol handling, and server-side architecture using Java.

## Features

- TCP-based client-server communication
- Redis-style command processing
- In-memory key-value storage
- Support for multiple client connections
- Command parsing and response handling
- Key expiration and TTL support
- Efficient data retrieval using Java data structures

## Tech Stack

- **Language:** Java
- **Build Tool:** Maven
- **Networking:** Java Socket API
- **Data Storage:** In-memory Java collections

## Project Structure

```text
java-redis/
├── src/
│   ├── main/
│   │   └── java/
│   └── test/
│       └── java/
├── your_program.sh
├── pom.xml
└── README.md
```

## How to Run

Clone the repository:

```bash
git clone https://github.com/SudarshanAg31/java-redis.git
cd java-redis
```

Build the project:

```bash
mvn clean package
```

Run the server using the appropriate Java command for the project.

## Learning Goals

This project is designed to explore:

- TCP/IP networking
- Client-server architecture
- Socket programming
- Protocol parsing
- In-memory databases
- Concurrent client handling
- Data structures and memory management
- Backend system design

## Future Improvements

- Persistence
- Replication
- Pub/Sub functionality
- Additional Redis-compatible commands
- Improved concurrency
- Performance optimization

## Author

**Sudarshan Agrawal**

GitHub: [@SudarshanAg31](https://github.com/SudarshanAg31)
