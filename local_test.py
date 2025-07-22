# Save this file as `local_test.py` in the root of your agent project.

import asyncio
import os

# Make sure the agent folder is in the Python path
# This assumes your agent code is in a folder like 'manager_agent'
# at the same level as this script.
import sys
sys.path.append(os.getcwd())

# Import the necessary components from your project files
from manager_agent.adk_app import adk_app, build_local_firestore_session_service
from vertexai.preview.generative_models import Part, Content

# --- Configuration ---
# The local image file to use for the test.
# Make sure this file exists in the same directory as this script.
initial_state = { "image_url": "https://firebasestorage.googleapis.com/v0/b/valued-mediator-461216-k7.firebasestorage.app/o/plant_diseases%2F1752943819354.jpg?alt=media&token=1e1963bb-0eb2-4161-bb2f-2f379e7184a2" }
TEST_IMAGE_FILE = "leaf_disease.jpeg" 
TEST_QUESTION = "Analyse the plant image and provide a detailed report on its health, diseases, and treatment options."
Image_url = "https://firebasestorage.googleapis.com/v0/b/valued-mediator-461216-k7.firebasestorage.app/o/plant_diseases%2F1752943819354.jpg?alt=media&token=1e1963bb-0eb2-4161-bb2f-2f379e7184a2"
TEST_USER_ID = "local_user_01"
# --------------------

async def main():
    """
    Runs a local test of the agent, including session creation and a multimodal query
    using a local image file.
    """
    print("--- Starting Local Agent Test ---")

    # 1. Get or create a session using the adk_app itself.
    # This more accurately simulates how the deployed agent will work.
    print(f"\nChecking for existing sessions for user '{TEST_USER_ID}'...")
    try:
        # Use the adk_app to list sessions for the user
        list_response = await adk_app.async_list_sessions(user_id=TEST_USER_ID)
        
        session = None
        if list_response and list_response.sessions:
            session = list_response.sessions[0]
            print(f"✅ Found existing session. ID: {session.id}")
        else:
            print("No existing sessions found. Creating a new one...")
            session = await adk_app.async_create_session(user_id=TEST_USER_ID , state=initial_state)
            print(f"✅ New session created. ID: {session.id}")
        
        if not session:
            raise Exception("Failed to get or create a session.")

    except Exception as e:
        print(f"❌ Error during session management: {e}")
        return

    # 2. Prepare the multimodal prompt from a local file
    print("\nPreparing multimodal prompt...")
    try:
        with open(TEST_IMAGE_FILE, "rb") as f:
            image_data = f.read()
        
        text_part = Part.from_text(TEST_QUESTION)
        image_url = Part.from_text(Image_url)
        
        # For local testing with adk_app, the message must be a dictionary
        # representing a Content object.
        message = Content(parts=[text_part, image_url]).to_dict()
        print(f"Image File: {TEST_IMAGE_FILE}")
        print(f"Question: {TEST_QUESTION}")

    except FileNotFoundError:
        print(f"❌ Error: Image file not found at '{TEST_IMAGE_FILE}'.")
        print("Please add the image to your project directory to run the test.")
        return
    except Exception as e:
        print(f"❌ Error reading image file: {e}")
        return


    # 3. Query the agent
    print("\n--- Querying Agent ---")
    try:
        # The agent.query() method allows for local, in-process testing.
        # The adk_app handles session management using the configured session_service_builder.
        async for event in adk_app.async_stream_query(
            message=message,
            session_id=session.id,
            user_id=session.user_id,
        ):
            print(f"\n[EVENT]: {event}")
    except Exception as e:
        print(f"❌ An error occurred while querying the agent: {e}")

    print("\n--- Local Agent Test Finished ---")


if __name__ == "__main__":
    # Before running, ensure you are authenticated with Google Cloud.
    # Open your terminal and run:
    # gcloud auth application-default login
    
    asyncio.run(main())
