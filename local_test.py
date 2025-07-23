#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
This script runs a local test of the weather agent. It creates a session
with a predefined location and then sends a text query to get the forecast.
"""

import asyncio
import os
import sys

# Ensure the agent project folder is in the Python path.
# This assumes your agent code is in a folder like 'weather_agent'
# at the same level as this script.
sys.path.append(os.getcwd())

# Import the necessary components from your project files.
# Make sure to replace 'weather_agent' with your actual agent package name if different.
from manager_agent.adk_app import adk_app
from vertexai.preview.generative_models import Part, Content

# --- Configuration ---

# This initial state provides the necessary location data for your
# `get_seven_day_forecast` tool to function on the first turn.
# Location is set to London, UK.
initial_state = {
    "latitude": 51.5072,
    "longitude": -0.1276,
    "timezone": "Europe/London"
}

TEST_QUESTION = "What is the 7-day weather forecast?"
TEST_USER_ID = "local_weather_user_01"
# --------------------

async def main():
    """
    Runs a local test of the weather agent, including session creation
    and a text-based query.
    """
    print("--- Starting Local Weather Agent Test ---")

    # 1. Get or create a session.
    # The session is initialized with the latitude and longitude state.
    print(f"\nChecking for existing sessions for user '{TEST_USER_ID}'...")
    try:
        list_response = await adk_app.async_list_sessions(user_id=TEST_USER_ID)
        
        session = None
        if list_response and list_response.sessions:
            session = list_response.sessions[0]
            # Optional: You might want to update the state of an existing session
            # await adk_app.async_update_session(session_id=session.id, state_delta=initial_state)
            print(f"✅ Found existing session. ID: {session.id}")
        else:
            print("No existing sessions found. Creating a new one...")
            session = await adk_app.async_create_session(user_id=TEST_USER_ID, state=initial_state)
            print(f"✅ New session created with initial location state. ID: {session.id}")
        
        if not session:
            raise Exception("Failed to get or create a session.")

    except Exception as e:
        print(f"❌ Error during session management: {e}")
        return

    # 2. Prepare the text prompt.
    print("\nPreparing text prompt...")
    try:
        text_part = Part.from_text(TEST_QUESTION)
        
        # For local testing with adk_app, the message must be a dictionary
        # representing a Content object.
        message = Content(parts=[text_part]).to_dict()
        print(f"Question: {TEST_QUESTION}")

    except Exception as e:
        print(f"❌ Error preparing the prompt: {e}")
        return

    # 3. Query the agent and stream the response.
    print("\n--- Querying Agent ---")
    try:
        # Use adk_app.async_stream_query to test the agent locally.
        async for event in adk_app.async_stream_query(
            message=message,
            session_id=session.id,
            user_id=session.user_id,
        ):
            print(f"\n[AGENT_EVENT]: {event}")
    except Exception as e:
        print(f"❌ An error occurred while querying the agent: {e}")

    print("\n--- Local Agent Test Finished ---")


if __name__ == "__main__":
    # Before running, ensure you are authenticated with Google Cloud.
    # In your terminal, run:
    # gcloud auth application-default login
    
    # Also ensure your agent's package (`weather_agent` in this example)
    # is discoverable in your Python path.
    
    asyncio.run(main())