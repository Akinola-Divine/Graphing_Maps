Graphing_Maps
=============

**A Java-based OpenStreetMap routing engine with GeoJSON + HTTP API**

Graphing_Maps is a Java project that simulates the core functionality of a real-world GPS system.\
It compiles **OpenStreetMap (OSM)** data into a compact **weighted directed graph**, computes optimal routes using **Dijkstra's algorithm or A*** search, and exposes routing results through a **lightweight HTTP server** that returns **GeoJSON** for easy map visualization.

* * * * *

Features
--------

-   **OSM Compilation**

    -   Multi-pass SAX parsing of `.osm` XML files

    -   Converts road networks into a routable graph

    -   Preserves original road geometry for visualization

-   **Routing Engine**

    -   Dijkstra and A* shortest-path algorithms

    -   Distance-based and time-based routing

    -   Proper handling of one-way and bidirectional roads

-   **Turn-by-Turn Instructions**

    -   Generates human-readable navigation instructions

    -   Based on edge geometry and street metadata

-   **GeoJSON Output**

    -   Routes returned as GeoJSON `LineString`

    -   Compatible with Leaflet, Mapbox, OpenLayers

-   **HTTP REST API**

    -   Built-in Java HTTP server

    -   Simple `/route` endpoint for routing queries

* * * * *

System Architecture
-------------------

OSM (.osm file)\
↓\
OSMCompiler (3-pass parsing)\
↓\
WeightedDigraph + EdgeAttributes + EdgeGeometry\
↓\
RoutingEngine (Dijkstra / A*)\
↓\
RouteCLI / RouteServer\
↓\
GeoJSON + Instructions

* * * * *

Project Structure
-----------------

src/main/java/codes/\
├── Bag.java\
├── Digraph.java\
├── WeightedDigraph.java\
├── Edge.java\
├── EdgeAttributes.java\
├── EdgeGeometry.java\
├── Grid.java\
├── LocalProjection.java\
├── Point.java\
├── SegmentSnapper.java\
├── Reconstruction.java\
├── ShortestPathAlgorithms.java\
├── RoutingEngine.java\
├── RoutingResult.java\
├── RouteCLI.java\
├── RouteServer.java\
├── Instruction.java\
├── InstructionGenerator.java\
├── ValidationHarness.java\
└── Main.java

* * * * *

Getting Started
---------------

### Requirements

-   Java **11 or later**

-   An OpenStreetMap `.osm` file (e.g. `pei.osm`)

* * * * *

Compile OSM Data
----------------

The `Main` class compiles an OSM file into a routing graph.

Run:\
java codes.Main

Example output:

V=12345 E=45678\
attrs.edgeCount=45678

* * * * *

Routing Algorithms
------------------

### Supported Algorithms

-   **Dijkstra** --- full shortest-path computation

-   **A*** --- heuristic-guided search using vertex coordinates

### Optimization Metrics

-   **DISTANCE** (meters)

-   **TIME** (seconds)

### Example Usage

RoutingEngine engine =\
new RoutingEngine(graph, attrs, vertexStore, maxSpeedMetersPerSec);

RoutingEngine.Route route =\
engine.routeDistanceAStar(startVertex, goalVertex);

* * * * *

Geometry & Projection
---------------------

-   Uses WGS84 latitude/longitude coordinates

-   Converts to local meters using a tangent-plane projection

-   Preserves full road polylines for accurate rendering

-   Supports snapping and reconstruction of routes

* * * * *

Validation
----------

-   `ValidationHarness` verifies graph correctness

-   Ensures edge geometry and routing consistency

* * * * *

Libraries Used
--------------

-   SAX Parser (XML processing)

-   Eclipse Collections (primitive maps)

-   libGDX utilities (efficient primitive arrays)
