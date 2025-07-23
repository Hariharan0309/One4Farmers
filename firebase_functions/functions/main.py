from firebase_functions import https_fn
from firebase_functions.options import set_global_options, MemoryOption
from firebase_admin import initialize_app, firestore, storage

import os
import vertexai
from vertexai import agent_engines, generative_models
from vertexai.preview.generative_models import Part, Content, GenerativeModel
import json
import requests

# --- Configuration ---
# Best practice: Load configuration from environment variables with defaults.
PROJECT_ID = os.environ.get("GCP_PROJECT", "valued-mediator-461216-k7")
LOCATION = os.environ.get("GCP_LOCATION", "us-central1")
REASONING_ENGINE_ID = os.environ.get("REASONING_ENGINE_ID", "2569752188159000576")
# --------------------

# Initialize Firebase Admin SDK once in the global scope.
initialize_app()
set_global_options(
    max_instances=10,
    memory=MemoryOption.GB_1,
    timeout_sec=300,  # Increase timeout to 5 minutes
)

# --- Lazy Initialization for Vertex AI Client ---
_remote_app = None

def get_remote_app():
    """
    Initializes and returns the Vertex AI remote app, ensuring it's only
    created once per function instance.
    """
    global _remote_app
    if _remote_app is None:
        print("Initializing Vertex AI client for the first time...")
        engine_resource_name = f"projects/{PROJECT_ID}/locations/{LOCATION}/reasoningEngines/{REASONING_ENGINE_ID}"
        print(f"Connecting to Reasoning Engine: {engine_resource_name}")
        _remote_app = agent_engines.get(engine_resource_name)
        print("Vertex AI client initialized.")
    return _remote_app


@https_fn.on_request()
def get_or_create_session(req: https_fn.Request) -> https_fn.Response:
    """
    An HTTP endpoint that finds an existing session for a user_id,
    or creates a new one if none are found.
    Expects a JSON body with 'user_id' and an optional 'state' object.
    """
    try:
        request_json = req.get_json(silent=True)
        if not request_json or 'user_id' not in request_json:
            return https_fn.Response("Error: Please provide 'user_id' in the JSON body.", status=400)
        
        user_id = request_json['user_id']
        initial_state = request_json.get('state', {})

        # Get the initialized remote app
        remote_app = get_remote_app()

        # --- Find or Create a Session ---
        print(f"Checking for existing sessions for user '{user_id}'...")
        list_sessions_response = remote_app.list_sessions(user_id=user_id)
        
        session_id = None
        session_state = {}

        if list_sessions_response and list_sessions_response.get('sessions'):
            # Use the first existing session
            remote_session = list_sessions_response['sessions'][0]
            session_id = remote_session.get('id')
            session_state = remote_session.get('state', {})
            print(f"Found existing session with ID: {session_id}")
        else:
            # Or create a new one if none exist
            print(f"No existing sessions found for user '{user_id}'. Creating a new one.")
            new_session = remote_app.create_session(user_id=user_id, state=initial_state)
            session_id = new_session.get('id')
            session_state = new_session.get('state', {})
            print(f"Created new session with ID: {session_id}")
        
        if not session_id:
             raise Exception("Failed to get or create a session ID.")

        # Prepare the JSON response with both session ID and state
        response_data = json.dumps({
            "session_id": session_id,
            "state": session_state
        })
        
        return https_fn.Response(response_data, mimetype="application/json")

    except Exception as e:
        print(f"An internal error occurred: {e}")
        return https_fn.Response(f"An internal error occurred: {e}", status=500)

@https_fn.on_request()
def stream_query_agent(req: https_fn.Request) -> https_fn.Response:
    """
    An HTTP endpoint that queries the agent with either text or audio.
    Expects a JSON body with 'user_id', 'session_id', and either 'message' (text)
    or 'audio_url' (a GCS link to an audio file).
    """
    try:
        request_json = req.get_json(silent=True)
        # Check for required fields
        if not request_json or 'user_id' not in request_json or 'session_id' not in request_json:
            return https_fn.Response("Error: Please provide 'user_id' and 'session_id'.", status=400)
        
        user_id = request_json['user_id']
        session_id = request_json['session_id']
        
        # --- Prepare the message for the agent ---
        message_parts = []
        
        text_message = request_json.get('message')
        audio_url = request_json.get('audio_url')
        image_url = request_json.get('image_url')
        
        if audio_url:
            print(f"Received audio URL: {audio_url}")
            response = requests.get(audio_url)
            response.raise_for_status()
            audio_data = response.content
            
            # Assume the audio is always .m4a (AAC in an MP4 container)
            mime_type = "audio/mp4"
            print(f"Using hardcoded MIME type: '{mime_type}' for .m4a file.")

            audio_part = Part.from_data(data=audio_data, mime_type=mime_type)
            message_parts.append(audio_part)
            print("Successfully processed audio URL into a message Part.")

            if not text_message:
                text_message = "Listen to the audio, understand the user's question, and respond in the language spoken in the audio."
                print(f"No text message provided with audio. Using default: '{text_message}'")

        if image_url:
            print(f"Received image URL: {image_url}")
            response = requests.get(image_url)
            response.raise_for_status()
            image_data = response.content
            
            # Try to get mime_type from headers, default to jpeg
            mime_type = response.headers.get('content-type', 'image/jpeg')
            print(f"Inferred MIME type: '{mime_type}' for image.")

            image_part = Part.from_data(data=image_data, mime_type=mime_type)
            message_parts.append(image_part)
            print("Successfully processed image URL into a message Part.")

        if text_message:
            message_parts.append(Part.from_text(text_message))
            print(f"Added text message: '{text_message}'")
        
        if not message_parts:
            return https_fn.Response("Error: Please provide a 'message', 'audio_url', or 'image_url'.", status=400)

        # --- THIS IS THE FIX: Create a Content object and convert it to a dictionary ---
        # This matches the `Dict[str, Any]` type expected by the function.
        final_message = Content(parts=message_parts, role="user").to_dict()

        remote_app = get_remote_app()

        # --- Query the Agent and Collect the Streamed Response ---
        print(f"Streaming query for session '{session_id}'...")
        full_response_text = ""
        for event in remote_app.stream_query(
            user_id=user_id,
            session_id=session_id,
            message=final_message, # Send the correctly formatted dictionary
        ):
            print(f"\n[EVENT]: {event}")
            if event.get('content') and event.get('content').get('parts'):
                for part in event['content']['parts']:
                    if part.get('text'):
                        full_response_text += part['text']
        
        print(f"Full agent response: {full_response_text}")
        
        response_data = json.dumps({"response": full_response_text})
        return https_fn.Response(response_data, mimetype="application/json")

    except Exception as e:
        print(f"An error occurred in stream_query_agent: {e}")
        return https_fn.Response(f"An internal error occurred: {e}", status=500)
