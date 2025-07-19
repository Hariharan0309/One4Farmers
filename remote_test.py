import vertexai
from vertexai.preview import reasoning_engines
from vertexai import agent_engines

from dotenv import load_dotenv
from google.auth import impersonated_credentials
from google.oauth2 import service_account
import google.auth


initial_state = {
    "user_name": "Hariharan",
    "reminders": ["Drink water", "Take a walk","Read a book"],
}

remote_app = vertexai.agent_engines.get(
    "projects/673680613234/locations/us-central1/reasoningEngines/2569752188159000576"
)
print(remote_app)

# Get existing sessions for the user
list_sessions_response = remote_app.list_sessions(user_id="u_113")
print(f"List sessions response: {list_sessions_response}")
sessions = list_sessions_response['sessions']
print(f"Found {len(sessions)} existing sessions.")
# sessions = list_sessions_response

if sessions:
    # Use the first existing session
    remote_session = sessions[0]
    print(f"Using existing session: {remote_session['id']}")
    print(f"Session state: {remote_session['state']}")
else:
    # Or create a new one if none exist
    print("No existing sessions found. Creating a new one.")
    remote_session = remote_app.create_session(user_id="u_113", state=initial_state)
    print(f"Created new session: {remote_session['id']}")
for event in remote_app.stream_query(
    user_id="u_113",
    session_id=remote_session["id"],
    message="Hello",
):
    print(event)