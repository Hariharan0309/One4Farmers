from firebase_functions import https_fn
from firebase_functions.options import set_global_options, MemoryOption
from firebase_admin import initialize_app

import vertexai
from vertexai import agent_engines
import json

# --- Configuration ---
PROJECT_ID = "valued-mediator-461216-k7"
LOCATION = "us-central1"
REASONING_ENGINE_ID = "2569752188159000576"
# --------------------

# Initialize Firebase Admin SDK once in the global scope.
initialize_app()
set_global_options(max_instances=10, memory=MemoryOption.GB_1)

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
        _remote_app = agent_engines.get("projects/673680613234/locations/us-central1/reasoningEngines/2569752188159000576")
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

