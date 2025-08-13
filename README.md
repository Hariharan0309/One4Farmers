# One4Farmers

**AI-powered Farming Expert App: a mobile-first, voice-guided digital assistant for farmers. Get real-time insights on markets, crop health, weather, finance, and government schemes. Empowering informed decisions for thriving agriculture.**

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [API Endpoints](#api-endpoints)
- [Setup and Deployment](#setup-and-deployment)

## Overview

One4Farmers is a comprehensive platform designed to serve as a digital companion for farmers. It leverages the power of Google Cloud and Vertex AI to provide a suite of tools through a conversational, voice-first interface. The application aims to bridge the information gap for farmers by providing easy access to critical data, a marketplace for trade, and a community for collaboration.

The core of the application is a sophisticated AI agent built with the Vertex AI Reasoning Engine. This agent can understand user queries in natural language (including voice), analyze images, and perform complex tasks by orchestrating a set of specialized sub-agents and tools.

## Key Features

**🤖 AI-Powered Conversational Agent**: A central, intelligent agent that understands user intent and orchestrates tasks related to weather, market analysis, and crop management.

- **🛒 Marketplace Hub**: A fully-functional marketplace where farmers can:
  - List their produce for sale (`sell_product`).
    - Browse and purchase products from others in their region or nationwide (`list_products`).
    - Complete transactions securely (`purchase_product`).

- **📈 Real-time Market Prices**: Fetches the latest commodity prices from government APIs (`data.gov.in`) to help farmers make informed pricing decisions when selling their crops.
- **📸 Visual Crop & Soil Analysis**: Users can upload an image of their crops or soil. The `image_analyzer_agent` uses Gemini's multimodal capabilities to identify potential diseases, pests, nutrient deficiencies, or soil types and recommend suitable crops.
- **🌦️ Personalized Weather Advisory**: The `weather_agent` provides location-based weather forecasts and translates the data into actionable farming advice, such as when to irrigate or postpone pesticide spraying.
- **💬 Multi-lingual Community Chat**: A real-time chat feature where farmers can connect, ask questions, and share knowledge. Messages are automatically translated between English, Hindi, and Tamil to foster a wider community.
- **📦 Order Management & Delivery**:
  - Users can track their purchase history (`list_orders`).
  - A dedicated dashboard for delivery agents to view and manage their assigned orders (`get_agent_dashboard_orders`).
  - Agents can update order statuses in real-time (`delivery_update`).
- **🗣️ Voice-First Interaction**: Supports audio input for queries and chat messages, making the app accessible to users with varying levels of literacy.

## Technology Stack

- **Backend**: **Google Cloud Functions for Firebase** (Python) for serverless, event-driven architecture.
- **AI / Machine Learning**:
  - **Vertex AI Reasoning Engine**: The core framework for building and deploying the multi-agent system.
  - **Vertex AI Gemini 1.5 Pro**: The underlying Large Language Model for all agents.
  - **Google Cloud Speech-to-Text**: For transcribing user's voice messages.
  - **Google Cloud Translate API**: For real-time translation in the community chat.
  - **Vertex AI RAG Service**: To ground the agent in specific knowledge about government schemes and agricultural practices.
- **Database**: **Google Cloud Firestore** for storing user sessions, products, orders, and chat messages in a scalable NoSQL database.
- **Real-time Notifications**: **Firebase Cloud Messaging (FCM)** to notify users of new messages in the community chat.

## Project Structure

```
.
├── firebase_functions/      # Backend logic deployed as Cloud Functions
│   └── functions/
│       └── main.py          # All HTTP endpoints and Firestore triggers
├── manager_agent/           # Source code for the Vertex AI Reasoning Engine
│   ├── adk_app.py           # Main ADK application definition
│   ├── agent.py             # The top-level manager agent
│   ├── firestore/           # Custom Firestore session service for the ADK
│   └── sub_agents/          # Specialized agents for specific tasks
│       ├── image_analyzer_agent/
│       ├── market_agent/
│       └── weather_agent/
├── deploy.py                # Script to deploy the Reasoning Engine to Vertex AI
└── requirements.txt         # Python dependencies
```

## API Endpoints

The application is exposed via a set of HTTP Cloud Functions:

- **Agent Interaction**:
  - `POST /get_or_create_session`: Initializes or retrieves a user session with the agent.
  - `POST /stream_query_agent`: Sends a text, audio, or image query to the agent.
- **Marketplace**:
  - `GET /list_products`: Lists products for sale based on location.
  - `POST /sell_product`: Lists a new product for sale.
  - `POST /purchase_product`: Purchases one or more products.
  - `GET /list_user_products`: Lists all products a specific user is selling.
- **Order Management**:
  - `GET /list_orders`: Lists a user's purchase history.
  - `POST /rate_product`: Allows a user to rate a purchased product.
- **Delivery Agent**:
  - `GET /get_agent_dashboard_orders`: Fetches all orders assigned to a delivery agent.
  - `POST /delivery_update`: Updates the status of an order.
- **Community Chat**:
  - `GET /getCommunityMessages`: Fetches the latest chat messages.
  - `POST /sendCommunityMessage`: Sends a new text or audio message to the chat.

## Setup and Deployment

### Prerequisites

1. A Google Cloud Project with the following APIs enabled:
    - Cloud Functions API
    - Cloud Build API
    - Vertex AI API
    - Cloud Firestore API
    - Cloud Speech-to-Text API
    - Cloud Translation API
2. Firebase CLI installed and configured for your project.
3. Google Cloud SDK (`gcloud`) installed and authenticated.
4. A custom Service Account with appropriate permissions for Reasoning Engine execution (e.g., `reasoning-engine-runner`).

### 1. Deploying Firebase Functions

The backend services are deployed as a single set of Cloud Functions.

```bash
# Navigate to the functions directory
cd firebase_functions/functions

# Deploy all functions
firebase deploy --only functions
```

### 2. Deploying the Vertex AI Reasoning Engine

The `deploy.py` script handles the creation or update of the agent on Vertex AI. It packages the agent code, sets environment variables, and registers the agent with the Reasoning Engine service.

```bash
# From the project root
python deploy.py
```
