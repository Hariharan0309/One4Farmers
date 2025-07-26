from firebase_functions import https_fn
from firebase_functions.options import set_global_options, MemoryOption
from firebase_admin import initialize_app, firestore, storage

import os
import vertexai
from vertexai import agent_engines, generative_models
from vertexai.generative_models import Part, Content
import json
import requests
from datetime import datetime

# --- Custom JSON Encoder ---
class DateTimeEncoder(json.JSONEncoder):
    """Custom JSON encoder to handle datetime objects and Firestore Timestamps."""
    def default(self, o):
        if isinstance(o, datetime):
            return o.isoformat()
        # Duck-typing to handle Firestore's Timestamp without a fragile import.
        # This is more robust against library version changes.
        if hasattr(o, 'to_datetime') and callable(o.to_datetime):
            return o.to_datetime().isoformat()
        return super().default(o)

# --- Configuration ---
# Best practice: Load configuration from environment variables with defaults.
PROJECT_ID = os.environ.get("GCP_PROJECT", "valued-mediator-461216-k7")
LOCATION = os.environ.get("GCP_LOCATION", "us-central1")
REASONING_ENGINE_ID = os.environ.get("REASONING_ENGINE_ID", "2569752188159000576")
DATABASE = os.environ.get("FIRESTORE_DATABASE", "one4farmers")
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
_db = None

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


def get_firestore_client():
    """
    Initializes and returns the Firestore client, ensuring it's only
    created once per function instance.
    """
    global _db
    if _db is None:
        print("Initializing Firestore client for the first time...")
        _db = firestore.Client(project=PROJECT_ID, database=DATABASE)
        print("Firestore client initialized.")
    return _db


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


@https_fn.on_request()
def list_products(req: https_fn.Request) -> https_fn.Response:
    """
    An HTTP endpoint that lists all available products from the Firestore
    'products' collection. It returns all products, including those that are
    out of stock (quantity is 0).
    - If 'state' and 'district' are provided as query parameters, it filters by location.
    - If 'user_id' is provided without a location, it attempts to find the user's
      location from their active session.
    - If a 'user_id' is provided, it will always filter out products listed by that user.
    """
    try:
        # Make the function robust by checking for parameters in both the
        # JSON body (for POST requests) and query args (for GET requests).
        request_json = req.get_json(silent=True) or {}
        user_id = req.args.get("user_id") or request_json.get("user_id")
        state = req.args.get("state") or request_json.get("state")
        district = req.args.get("district") or request_json.get("district")

        print(f"list_products invoked with user_id: {user_id}, state: {state}, district: {district}")
        # If location is not provided directly, try to fetch it from the user's session.
        if user_id and not (state and district):
            try:
                print(f"Location not provided for user '{user_id}'. Attempting to fetch from session...")
                remote_app = get_remote_app()
                list_sessions_response = remote_app.list_sessions(user_id=user_id)
                if list_sessions_response and list_sessions_response.get('sessions'):
                    session_state = list_sessions_response['sessions'][0].get('state', {})
                    # Use session location if not provided in args
                    state = state or session_state.get("state")
                    district = district or session_state.get("district")
                    print(f"Found location from session: State={state}, District={district}")
            except Exception as e:
                # Log the error but don't fail the request.
                # The query can proceed without location filters if necessary.
                print(f"Could not fetch session location for user '{user_id}': {e}")

        db = get_firestore_client()
        query = db.collection("products")

        # All filters are now applied in the code after fetching to make the function
        # more robust and avoid any reliance on composite indexes in Firestore.

        docs = query.stream()

        products_list = []
        for doc in docs:
            product_data = doc.to_dict()
            # Apply location and user filters in the code.
            if state and product_data.get("state") != state:
                continue
            if district and product_data.get("district") != district:
                continue
            # Apply the filter for the user's own products here in the code.
            if user_id and product_data.get("seller_id") == user_id:
                continue

            product_data["product_id"] = doc.id  # Add the document ID to the data
            products_list.append(product_data)
        
        print(f"Found {len(products_list)} products matching the criteria.")
        print(f"Products list: {products_list}")

        return https_fn.Response(json.dumps({"products": products_list}, cls=DateTimeEncoder), mimetype="application/json")

    except Exception as e:
        print(f"An error occurred while listing products: {e}")
        return https_fn.Response(f"An internal error occurred: {e}", status=500)


@firestore.transactional
def _transactional_purchase(transaction, product_ref, quantity_to_buy: int):
    """
    Helper to perform a transactional read-modify-write for a purchase.
    This ensures the quantity check and update are atomic.
    """
    snapshot = product_ref.get(transaction=transaction)
    if not snapshot.exists:
        raise ValueError("Product not found.")

    data = snapshot.to_dict()
    current_quantity = data.get("quantity_available")

    if current_quantity is None:
        raise ValueError("Product data is corrupt: missing 'quantity_available' field.")

    if current_quantity < quantity_to_buy:
        raise ValueError(f"Insufficient quantity. Only {current_quantity} available.")

    new_quantity = current_quantity - quantity_to_buy
    transaction.update(product_ref, {"quantity_available": new_quantity})
    return data, new_quantity


@https_fn.on_request()
def purchase_product(req: https_fn.Request) -> https_fn.Response:
    """
    An HTTP endpoint for an app to purchase one or more products from a shopping cart.
    Expects a JSON body with 'user_id' and a 'product_list', where product_list
    is a list of objects, each with 'product_id' and 'quantity'.
    e.g., {"user_id": "x", "product_list": [{"product_id": "y", "quantity": 1}]}
    """
    try:
        request_json = req.get_json(silent=True)
        if not request_json:
            return https_fn.Response(json.dumps({"error": "Invalid JSON body."}), status=400, mimetype="application/json")

        user_id = request_json.get("user_id")
        product_list = request_json.get("product_list")

        if not user_id or not product_list:
            return https_fn.Response(json.dumps({"error": "Missing 'user_id' or 'product_list' in request body."}), status=400, mimetype="application/json")

        if not isinstance(product_list, list):
            return https_fn.Response(json.dumps({"error": "'product_list' must be a list of products."}), status=400, mimetype="application/json")

        successful_orders = []
        failed_orders = []
        db = get_firestore_client()

        for item in product_list:
            product_id = item.get("product_id")
            quantity = item.get("quantity")

            try:
                if not product_id or not quantity:
                    raise ValueError("Each item in 'product_list' must contain 'product_id' and 'quantity'.")

                if not isinstance(quantity, int) or quantity <= 0:
                    raise ValueError("'quantity' must be a positive integer.")

                product_ref = db.collection("products").document(product_id)

                product_doc = product_ref.get()
                if not product_doc.exists:
                    raise ValueError(f"Product with ID {product_id} not found.")

                if product_doc.to_dict().get("seller_id") == user_id:
                    raise ValueError("You cannot purchase your own product.")

                transaction = db.transaction()
                product_data, new_quantity = _transactional_purchase(transaction, product_ref, quantity)

                # --- Pre-determine the delivery agent to avoid a second write to the order ---
                chosen_agent_doc_id = None
                agent_id_to_assign = None
                try:
                    agents_ref = db.collection("delivery_agents")
                    agents_stream = agents_ref.stream()

                    available_agents = []
                    for agent in agents_stream:
                        agent_data = agent.to_dict()
                        agent_data['id'] = agent.id
                        if 'orders_assigned' in agent_data and 'agent_id' in agent_data:
                            available_agents.append(agent_data)

                    if not available_agents:
                        print("Warning: No available delivery agents found. Skipping assignment.")
                    else:
                        # Simple load balancing: find the agent with the fewest orders.
                        chosen_agent = min(available_agents, key=lambda x: len(x.get('orders_assigned', [])))
                        chosen_agent_doc_id = chosen_agent['id']
                        agent_id_to_assign = chosen_agent['agent_id']
                        print(f"Pre-selected agent {agent_id_to_assign} (doc: {chosen_agent_doc_id}) for new order.")
                except Exception as agent_e:
                    # Log the agent assignment error but don't fail the whole purchase
                    print(f"Warning: Failed to pre-select delivery agent. Error: {agent_e}")

                # Use a batch write to create the order and update the agent document atomically.
                batch = db.batch()
                order_ref = db.collection("orders").document()
                new_order_id = order_ref.id
                order_data = {
                    "product_id": product_id, "buyer_id": user_id, "seller_id": product_data.get("seller_id"),
                    "product_name": product_data.get("product_name", "Unknown Product"), "quantity": quantity,
                    "order_time": firestore.SERVER_TIMESTAMP, "status": "dispatched", "agent_assigned": agent_id_to_assign,
                }
                batch.set(order_ref, order_data)
                if chosen_agent_doc_id:
                    agent_ref = db.collection("delivery_agents").document(chosen_agent_doc_id)
                    batch.update(agent_ref, {"orders_assigned": firestore.ArrayUnion([new_order_id])})
                batch.commit()
                print(f"Successfully created order and assigned agent in one atomic batch write.")

                successful_orders.append({"product_id": product_id, "order_id": new_order_id, "remaining_quantity": new_quantity})

                # --- Update session state in Firestore ---
                try:
                    remote_app = get_remote_app()
                    list_sessions_response = remote_app.list_sessions(user_id=user_id)
                    if list_sessions_response and list_sessions_response.get('sessions'):
                        session_id = list_sessions_response['sessions'][0].get('id')
                        session_ref = db.collection("adk_sessions").document(session_id)

                        @firestore.transactional
                        def _update_session_state(transaction, ref):
                            session_doc = ref.get(transaction=transaction)
                            if not session_doc.exists:
                                return
                            state = session_doc.to_dict().get("state", {})
                            # Get the list of order_ids, or an empty list if it doesn't exist.
                            order_ids = state.get("order_ids", [])
                            order_ids.append(new_order_id)
                            transaction.update(ref, {"state.order_ids": order_ids, "updateTime": firestore.SERVER_TIMESTAMP})
                        
                        session_transaction = db.transaction()
                        _update_session_state(session_transaction, session_ref)
                        print(f"Successfully updated session state for order {new_order_id}")
                except Exception as session_e:
                    # Log the session update error but don't fail the whole purchase
                    print(f"Warning: Failed to update session state for order {new_order_id}. Error: {session_e}")

            except Exception as e:
                failed_orders.append({"product_id": product_id, "error": str(e)})

        status = "success"
        if failed_orders and successful_orders:
            status = "partial_success"
        elif failed_orders and not successful_orders:
            status = "failed"

        response_data = {"status": status, "successful_orders": successful_orders, "failed_orders": failed_orders}
        http_status = 400 if status == "failed" else 200
        return https_fn.Response(json.dumps(response_data, cls=DateTimeEncoder), status=http_status, mimetype="application/json")

    except ValueError as ve:
        return https_fn.Response(json.dumps({"error": str(ve)}), status=400, mimetype="application/json")
    except Exception as e:
        print(f"An error occurred during purchase: {e}")
        return https_fn.Response(json.dumps({"error": f"An internal error occurred: {e}"}), status=500, mimetype="application/json")


@https_fn.on_request()
def list_orders(req: https_fn.Request) -> https_fn.Response:
    """
    An HTTP endpoint that lists all orders for a given user from the 'orders' collection.
    Expects a 'user_id' in the request body or query parameters.
    """
    try:
        # Make the function robust by checking for parameters in both the
        # JSON body (for POST requests) and query args (for GET requests).
        request_json = req.get_json(silent=True) or {}
        user_id = req.args.get("user_id") or request_json.get("user_id")

        if not user_id:
            return https_fn.Response(json.dumps({"error": "Missing 'user_id' in request."}), status=400, mimetype="application/json")

        print(f"list_orders invoked for user_id: {user_id}")

        db = get_firestore_client()
        # Query the 'orders' collection, filtering by the buyer's ID.
        # The ordering is done in the application code to avoid needing a composite
        # index in Firestore for filtering on one field and ordering on another.
        query = db.collection("orders").where(
            filter=firestore.FieldFilter("buyer_id", "==", user_id)
        )

        docs = query.stream()

        orders_list = [doc.to_dict() | {"order_id": doc.id} for doc in docs]

        # Sort the list of orders by 'order_time' in descending order.
        orders_list.sort(key=lambda x: x.get("order_time"), reverse=True)

        print(f"Found {len(orders_list)} orders for user '{user_id}'.")
        return https_fn.Response(json.dumps({"orders": orders_list}, cls=DateTimeEncoder), mimetype="application/json")

    except Exception as e:
        print(f"An error occurred while listing orders: {e}")
        return https_fn.Response(json.dumps({"error": f"An internal error occurred: {e}"}), status=500, mimetype="application/json")


@https_fn.on_request()
def get_agent_dashboard_orders(req: https_fn.Request) -> https_fn.Response:
    """
    An HTTP endpoint that fetches all order details for a specific delivery agent.
    It first finds the agent by their agent_id, retrieves their list of assigned
    order IDs, and then fetches the details for each of those orders.
    Expects a 'agent_id' in the request body or query parameters.
    """
    try:
        request_json = req.get_json(silent=True) or {}
        agent_id = req.args.get("agent_id") or request_json.get("agent_id")

        if not agent_id:
            return https_fn.Response(json.dumps({"error": "Missing 'agent_id' in request."}), status=400, mimetype="application/json")

        print(f"get_agent_dashboard_orders invoked for agent_id: {agent_id}")

        db = get_firestore_client()

        # 1. Find the agent document by their agent_id
        agent_query = db.collection("delivery_agents").where(
            filter=firestore.FieldFilter("agent_id", "==", agent_id)
        ).limit(1)
        
        agent_docs = list(agent_query.stream())

        if not agent_docs:
            return https_fn.Response(json.dumps({"error": f"No delivery agent found with ID: {agent_id}"}), status=404, mimetype="application/json")

        agent_data = agent_docs[0].to_dict()
        order_ids = agent_data.get("orders_assigned", [])

        if not order_ids:
            print(f"Agent {agent_id} has no orders assigned.")
            return https_fn.Response(json.dumps({"orders": []}), mimetype="application/json")

        print(f"Found {len(order_ids)} assigned orders for agent {agent_id}. Fetching details...")

        # 2. Fetch order details for the assigned order IDs.
        # IMPORTANT: Firestore 'in' queries are limited to 30 items.
        # The following logic fetches each document individually to avoid issues
        # with special field filters like DOCUMENT_ID.
        all_orders = []
        if order_ids:
            orders_collection = db.collection("orders")
            for order_id in order_ids:
                doc = orders_collection.document(order_id).get()
                if doc.exists:
                    order_data = doc.to_dict()
                    order_data["order_id"] = doc.id
                    all_orders.append(order_data)

        # 3. Sort the results by order time
        all_orders.sort(key=lambda x: x.get("order_time"), reverse=True)

        print(f"Successfully fetched details for {len(all_orders)} orders.")
        return https_fn.Response(json.dumps({"orders": all_orders}, cls=DateTimeEncoder), mimetype="application/json")

    except Exception as e:
        print(f"An error occurred while fetching agent dashboard orders: {e}")
        return https_fn.Response(json.dumps({"error": f"An internal error occurred: {e}"}), status=500, mimetype="application/json")

@https_fn.on_request()
def delivery_update(req: https_fn.Request) -> https_fn.Response:
    """
    An HTTP endpoint for a delivery agent to update an order's status and
    simultaneously remove the order from their assigned list.
    Expects a JSON body with 'agent_id', 'order_id', and 'status'.
    """
    try:
        request_json = req.get_json(silent=True)
        if not request_json:
            return https_fn.Response(json.dumps({"error": "Invalid JSON body."}), status=400, mimetype="application/json")

        agent_id = request_json.get("agent_id")
        order_id = request_json.get("order_id")
        status = request_json.get("status")

        if not all([agent_id, order_id, status]):
            return https_fn.Response(json.dumps({"error": "Missing 'agent_id', 'order_id', or 'status' in request."}), status=400, mimetype="application/json")

        print(f"delivery_update invoked for agent '{agent_id}', order '{order_id}' with status '{status}'.")

        db = get_firestore_client()
        batch = db.batch()

        # 1. Schedule an update for the order document
        order_ref = db.collection("orders").document(order_id)
        batch.update(order_ref, {
            "status": status
        })

        # 2. Find the agent's document and schedule the removal of the order_id
        agent_query = db.collection("delivery_agents").where(filter=firestore.FieldFilter("agent_id", "==", agent_id)).limit(1)
        agent_docs = list(agent_query.stream())

        if agent_docs:
            agent_doc_ref = agent_docs[0].reference
            batch.update(agent_doc_ref, {"orders_assigned": firestore.ArrayRemove([order_id])})
            print(f"Scheduled removal of order '{order_id}' from agent '{agent_id}' list.")

        # 3. Commit both writes atomically
        batch.commit()

        return https_fn.Response(json.dumps({"message": f"Successfully updated order {order_id} to '{status}' and updated agent's list."}), mimetype="application/json")

    except Exception as e:
        print(f"An error occurred during delivery update: {e}")
        return https_fn.Response(json.dumps({"error": f"An internal error occurred: {e}"}), status=500, mimetype="application/json")


@https_fn.on_request()
def sell_product(req: https_fn.Request) -> https_fn.Response:
    """
    An HTTP endpoint for an app to list a new product for sale.
    Expects a JSON body with product details. Seller info and location
    can be omitted if available in the user's session.
    """
    try:
        request_json = req.get_json(silent=True)
        if not request_json:
            return https_fn.Response(json.dumps({"error": "Invalid JSON body."}), status=400, mimetype="application/json")

        # --- Get seller and location info, preferring request but falling back to session ---
        seller_id = request_json.get("seller_id") or request_json.get("user_id")
        seller_name = request_json.get("seller_name")
        state = request_json.get("state")
        district = request_json.get("district")

        # If any of the core identity/location fields are missing, try to get them from the session.
        if seller_id and not all([seller_name, state, district]):
            try:
                print(f"Partial info in request for user '{seller_id}'. Fetching from session...")
                remote_app = get_remote_app()
                list_sessions_response = remote_app.list_sessions(user_id=seller_id)
                if list_sessions_response and list_sessions_response.get('sessions'):
                    session_state = list_sessions_response['sessions'][0].get('state', {})
                    # Use session data as a fallback for any missing fields.
                    seller_name = seller_name or session_state.get("name")
                    state = state or session_state.get("state")
                    district = district or session_state.get("district")
                    print(f"Updated info from session: Seller={seller_name}, State={state}, District={district}")
            except Exception as e:
                # Log the error but don't fail the request yet. Validation will catch it.
                print(f"Could not fetch session data for user '{seller_id}': {e}")

        # --- Validate required fields ---
        # These fields must always be in the request body.
        required_product_fields = ["product_name", "product_type", "price_per_kg", "quantity_available"]
        missing_fields = [field for field in required_product_fields if field not in request_json]
        
        # Now validate the identity/location fields after attempting to fill from session.
        if not seller_id: missing_fields.append("seller_id or user_id")
        if not seller_name: missing_fields.append("seller_name")
        if not state: missing_fields.append("state")
        if not district: missing_fields.append("district")

        if missing_fields:
            return https_fn.Response(json.dumps({"error": f"Missing required fields: {', '.join(missing_fields)}"}), status=400, mimetype="application/json")

        # --- Prepare product data ---
        product_data = {
            "product_name": str(request_json["product_name"]).title(),
            "product_type": str(request_json["product_type"]).lower(),
            "state": str(state),
            "district": str(district),
            "seller_name": str(seller_name),
            "seller_id": str(seller_id),
            "price_per_kg": float(request_json["price_per_kg"]),
            "quantity_available": int(request_json["quantity_available"]),
            "rating": None,
            "listed_at": firestore.SERVER_TIMESTAMP,
        }

        db = get_firestore_client()
        update_time, doc_ref = db.collection("products").add(product_data)
        new_product_id = doc_ref.id

        # --- Update session state in Firestore ---
        try:
            remote_app = get_remote_app()
            list_sessions_response = remote_app.list_sessions(user_id=seller_id)
            if list_sessions_response and list_sessions_response.get('sessions'):
                session_id = list_sessions_response['sessions'][0].get('id')
                session_ref = db.collection("adk_sessions").document(session_id)

                @firestore.transactional
                def _update_session_state(transaction, ref):
                    session_doc = ref.get(transaction=transaction)
                    if not session_doc.exists:
                        return
                    state = session_doc.to_dict().get("state", {})
                    products_listed = state.get("product_listed_in_market", [])
                    new_listing_record = {
                        "product_id": new_product_id,
                        "product_name": product_data["product_name"],
                    }
                    products_listed.append(new_listing_record)
                    transaction.update(ref, {"state.product_listed_in_market": products_listed, "updateTime": firestore.SERVER_TIMESTAMP})
                
                session_transaction = db.transaction()
                _update_session_state(session_transaction, session_ref)
                print(f"Successfully updated session state for new product listing {new_product_id}")
        except Exception as session_e:
            print(f"Warning: Failed to update session state for new product listing {new_product_id}. Error: {session_e}")

        response_data = {"message": "Product listed successfully.", "product_id": new_product_id}
        return https_fn.Response(json.dumps(response_data), mimetype="application/json")

    except (ValueError, TypeError) as e:
        return https_fn.Response(json.dumps({"error": f"Invalid data type for a field: {e}"}), status=400, mimetype="application/json")
    except Exception as e:
        print(f"An error occurred while selling product: {e}")
        return https_fn.Response(json.dumps({"error": f"An internal error occurred: {e}"}), status=500, mimetype="application/json")
