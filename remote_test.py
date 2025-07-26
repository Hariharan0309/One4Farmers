import vertexai
from vertexai.preview import reasoning_engines
from vertexai import agent_engines

from dotenv import load_dotenv
from google.auth import impersonated_credentials
from google.oauth2 import service_account
import google.auth


initial_state = {
    "name": "Hari",
    "latitude": 51.5072,
    "longitude": -0.1276,
    "timezone": "Europe/London",
    "state": "Tamil Nadu",
    "district": "Villupuram",
    "weather_last_updated": None,  # Optional, can be set later
    "weather_forecast": None,  # Optional, can be set later
    "user_id": "u_113_sales"
}
USER_ID = "u_113_buyer"
PROMPT = "I would like to buy 1 kg of potatos. Can you help me with that?"

remote_app = vertexai.agent_engines.get(
    "projects/673680613234/locations/us-central1/reasoningEngines/2569752188159000576"
)
print(remote_app)

# Get existing sessions for the user
list_sessions_response = remote_app.list_sessions(user_id=USER_ID)
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
    remote_session = remote_app.create_session(user_id=USER_ID, state=initial_state)
    print(f"Created new session: {remote_session['id']}")
for event in remote_app.stream_query(
    user_id=USER_ID,
    session_id=remote_session["id"],
    message=PROMPT,
):
    print(event)