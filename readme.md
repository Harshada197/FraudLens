# FraudLens 🔍

![Spring Boot](https://img.shields.io/badge/Spring_Boot-F2F4F9?style=for-the-badge&logo=spring-boot)
![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-323330?style=for-the-badge&logo=javascript&logoColor=F7DF1E)
![Vis.js](https://img.shields.io/badge/Vis.js-Network_Graph-blue?style=for-the-badge)

**FraudLens** is an in-memory UPI transaction fraud detection and visualization engine.  

Instead of relying on heavy external graph databases, FraudLens implements a **custom in-memory graph engine** to detect complex money laundering patterns, identify mule accounts, and dynamically calculate account risk scores. The project includes a live-updating dashboard with an interactive network graph, allowing fraud analysts to visually trace illicit money flows.

## ✨ Key Features

- **Interactive Network Visualization**: Physics-based graph rendering using Vis.js.
- **Custom Graph Engine**: Pure Java implementation of Directed Graphs, avoiding the overhead of external dependencies like Neo4j.
- **Live Dashboard**: Auto-refreshing metrics showing total transactions, accounts monitored, and active fraud alerts.
- **Algorithmic Threat Detection**: Employs classical computer science algorithms (DFS, BFS, Priority Queues, Sliding Windows) to detect specific fraud patterns.
- **Dynamic Risk Scoring**: An additive risk model (`Base Pattern Score + Propagation Bonus + Behaviour Bonus`) that categorizes accounts into `NORMAL`, `AT_RISK`, `SUSPICIOUS`, and `FRAUD`.
- **Realistic Data Simulation**: An automated `DataSeeder` that generates 100 accounts with distinct professions (Student, Salaried, Merchant), simulates organic lifestyle transactions, and injects sophisticated fraud topologies.

## 🧠 Algorithms & Detection Strategies

FraudLens relies on a suite of custom detectors:

| Pattern | Detection Strategy | Algorithm / Data Structure | Time Complexity |
| :--- | :--- | :--- | :--- |
| **Circular Laundering** | Detects funds moving in a closed loop (A → B → C → A) to obscure origin. | **Depth First Search (DFS)** + Recursion Stack | `O(V + E)` |
| **Hub Accounts** | Identifies central "mule" accounts with abnormally high connection degrees. | **Max-Heap (Priority Queue)** | `O(n log k)` |
| **Rapid Hops (Layering)** | Flags burst transactions where funds are rapidly hopped between accounts (e.g. 3+ txns in 5 mins). | **Sliding Window** (Two Pointers) | `O(n)` |
| **Structuring / Smurfing** | Detects multiple transactions intentionally kept just below reporting thresholds. | Grouping & Aggregation | `O(n)` |
| **Risk Propagation** | Contaminates accounts based on their proximity (degrees of separation) to known fraud nodes. | **Breadth First Search (BFS)** | `O(V + E)` |
| **Behaviour Analysis** | Penalizes deviations from a profile's expected max amount, monthly frequency, and active hours. | Statistical Profiling | `O(n)` |

## 🛠️ Tech Stack

**Backend**
- **Java 17+**
- **Spring Boot** (Web, REST API)
- **Maven** (Build Tool)

**Frontend**
- **HTML5 & CSS3** (Vanilla, CSS Variables for Dark Theme)
- **JavaScript** (ES6+, Fetch API)
- **Vis.js** (Network Graph Rendering)

## 🚀 Getting Started

### Prerequisites
- Java Development Kit (JDK) 17 or higher
- Apache Maven

### Installation & Running

1. **Clone the repository** (or download the source):
   ```bash
   git clone https://github.com/yourusername/FraudLens.git
   cd FraudLens
   ```

2. **Compile and run the Spring Boot application**:
   ```bash
   mvn clean spring-boot:run
   ```

3. **Access the Dashboard**:
   Open your web browser and navigate to:
   ```text
   http://localhost:8080/fraudlens/
   ```

## 🎮 Usage

Once the application is running, the `DataSeeder` will automatically generate the accounts and transactions in-memory.

1. **Dashboard (`/`)**: View high-level metrics and active alerts.
2. **Graph View (`/graph.html`)**: Interact with the live transaction network.
   - Click on nodes (accounts) to see detailed statistics and risk factors.
   - Use the **Month / Week / Day** toggles to slice the data.
   - Use the **Checkboxes** to filter the graph by risk level.
3. **Accounts List (`/accounts.html`)**: View a tabular list of all 100 monitored accounts.
   - Click column headers to sort by Risk Score, Transactions, Name, etc.
   - Use the search bar to find specific Account IDs.


