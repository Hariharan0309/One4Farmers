from agent import manager_agent
import vertexai
from vertexai.preview.reasoning_engines import AdkApp
from firestore.firestore_session_service import FirestoreSessionService

PROJECT_ID = "valued-mediator-461216-k7"
LOCATION = "us-central1"
staging_bucket = 'gs://one4farmers'

vertexai.init(project=PROJECT_ID, location=LOCATION, staging_bucket=staging_bucket)


def build_local_firestore_session_service():
    return FirestoreSessionService(project=PROJECT_ID, database="one4farmers")

# Pass the *builder function* to session_service_builder
adk_app = AdkApp(
    agent=manager_agent,
    enable_tracing=True,
    session_service_builder=build_local_firestore_session_service, # Pass the function, not an instance
)
