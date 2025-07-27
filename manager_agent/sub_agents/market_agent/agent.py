from google.adk.agents import Agent
from google.adk.tools.tool_context import ToolContext
from google.cloud import firestore
import logging
from typing import Optional
import os
import json
import requests
from datetime import datetime

# It's good practice to initialize clients once.
# Loading configuration from environment variables makes the agent more portable.
PROJECT_ID = os.environ.get("GCP_PROJECT", "valued-mediator-461216-k7")
DATABASE = os.environ.get("FIRESTORE_DATABASE", "one4farmers")
INDIA_GOV_API_KEY = "579b464db66ec23bdd00000173eac38e3c8048be736d0ebd84df8bf9"
API_BASE_URL = "https://api.data.gov.in/resource/35985678-0d79-46b4-9ed6-6f13308a1d24"

try:
    db = firestore.Client(project=PROJECT_ID, database=DATABASE)
    # Use a single, flat collection for all products
    PRODUCTS_COLLECTION = "products"
    ORDERS_COLLECTION = "orders"
except Exception as e:
    logging.error(f"Failed to initialize Firestore client: {e}")
    db = None

def _singularize(name: str) -> str:
    """A very basic singularizer to handle common plurals like 'tomatoes' -> 'tomato'."""
    lowered = name.lower()
    # Handle cases like 'tomatoes', 'potatoes'
    if lowered.endswith('oes') and len(lowered) > 3:
        return name[:-2]
    # Handle cases like 'apples', but not 'grass' or 'is'
    if lowered.endswith('s') and not lowered.endswith('ss') and len(lowered) > 2:
        return name[:-1]
    return name

def get_latest_market_price_from_session_location(tool_context: ToolContext, commodity: str) -> dict:
    """
    Fetches the single most recent market price for a given commodity. It uses
    the state and district stored in the session state for the location.

    Args:
        tool_context: The context object providing access to session state.
        commodity: The name of the commodity (e.g., "tomato").

    Returns:
        A dictionary containing the data for the most recent record or an error message.
    """
    state = tool_context.state.get("state")
    district = tool_context.state.get("district")

    # Check if location information is present in the session state
    if not state or not district:
        return {
            "error": "State or District not found in session state. Please ask the user for their location first."
        }

    singular_commodity = _singularize(commodity)
    normalized_state = state.title()
    normalized_district = district.title()
    normalized_commodity = singular_commodity.title()

    print(f"[Tool] Using location from session: {normalized_district}, {normalized_state}")
    print(f"[Tool] Fetching latest price for: {normalized_commodity}")

    params = {
        "api-key": INDIA_GOV_API_KEY,
        "format": "json",
        "limit": 50, # Fetch more records to increase chance of finding a valid one
        "filters[State]": normalized_state,
        "filters[District]": normalized_district,
        "filters[Commodity]": normalized_commodity,
    }

    try:
        response = requests.get(API_BASE_URL, params=params, timeout=15)
        response.raise_for_status()
        data = response.json()

        records = data.get("records")
        if not records:
            print("[Tool] No records found for the given filters.")
            return {"message": f"No price data found for {normalized_commodity} in {normalized_district}, {normalized_state}."}

        # Filter for valid records that have the 'Arrival_Date' key.
        valid_records = [record for record in records if 'Arrival_Date' in record and record['Arrival_Date']]
        
        if not valid_records:
            print("[Tool] No records with valid arrival dates found.")
            return {"message": f"No price data with valid dates found for {normalized_commodity} in {normalized_district}, {normalized_state}."}

        # Sort to find the most recent record.
        latest_record = max(valid_records, key=lambda record: datetime.strptime(record['Arrival_Date'], '%d/%m/%Y'))
        print(f"[Tool] Found {len(valid_records)} valid records. The latest is from {latest_record['Arrival_Date']}.")

        # On success, return a structured dictionary.
        return {"latest_record": latest_record}

    except requests.exceptions.RequestException as e:
        print(f"[Tool] API request failed: {e}")
        return {"error": f"Failed to fetch data from the API. Error: {e}"}

def list_products_for_sale(
    tool_context: ToolContext,
    product_type: Optional[str] = None,
    product_name: Optional[str] = None,
    state: Optional[str] = None,
    district: Optional[str] = None,
) -> dict:
    """
    Lists products available for sale from the Firestore database.
    It shows products matching the user's location, as well as products
    available to everyone (e.g., fertilizers, seeds).
    Can be filtered by product_type and product_name.
    If state or district are not provided, it uses the values from the session.
    """
    if not db:
        return {"error": "Database connection is not available."}

    session_state = tool_context.state
    user_id = session_state.get("user_id")
    # Use session location if specific location is not requested
    query_state = state or session_state.get("state")
    query_district = district or session_state.get("district")

    if not query_state or not query_district:
        return {"error": "Location (state and district) is not available. Please ask the user for their location."}

    try:
        # Query 1: Products specific to the user's location
        local_query = db.collection(PRODUCTS_COLLECTION).where(
            filter=firestore.FieldFilter("state", "==", query_state)
        ).where(
            filter=firestore.FieldFilter("district", "==", query_district)
        )

        # Query 2: Products available to everyone (e.g., fertilizers, seeds)
        global_query = db.collection(PRODUCTS_COLLECTION).where(
            filter=firestore.FieldFilter("state", "==", "any")
        ).where(
            filter=firestore.FieldFilter("district", "==", "any")
        )

        # Apply optional filters to both queries
        if product_type:
            local_query = local_query.where(filter=firestore.FieldFilter("product_type", "==", product_type.lower()))
            global_query = global_query.where(filter=firestore.FieldFilter("product_type", "==", product_type.lower()))
        if product_name:
            # Normalize the product name to handle singular/plural forms
            normalized_name = _singularize(product_name)
            local_query = local_query.where(filter=firestore.FieldFilter("product_name", "==", normalized_name.title()))
            global_query = global_query.where(filter=firestore.FieldFilter("product_name", "==", normalized_name.title()))

        # The range filter on 'quantity_available' is removed from the query to avoid
        # needing a composite index. This filtering will be done in the application code.

        # Execute both queries and combine the results, avoiding duplicates.
        all_docs = {}
        for doc in local_query.stream():
            all_docs[doc.id] = doc
        for doc in global_query.stream():
            all_docs[doc.id] = doc  # Overwrites if duplicate, which is fine.

        products = []
        for doc_id, doc in all_docs.items():
            product_data = doc.to_dict()
            # Apply the filter for the user's own products here.
            if user_id and product_data.get("seller_id") == user_id:
                continue  # Skip this product as it belongs to the current user.

            # Apply the quantity filter here in the code.
            if product_data.get("quantity_available", 0) <= 0:
                continue

            product_data['product_id'] = doc.id
            products.append(product_data)

        if not products:
            return {"message": f"No products found for the specified criteria in {query_district}, {query_state} or nationwide."}

        return {"products": products}

    except Exception as e:
        logging.error(f"Error listing products: {e}")
        return {"error": f"An error occurred while fetching products: {e}"}


@firestore.transactional
def _update_quantity_in_transaction(transaction, product_ref, quantity_to_buy):
    """Helper function to perform the transactional update."""
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
    # Return the full product data for use in the calling function, and the new quantity.
    return data, new_quantity


def purchase_product(
    tool_context: ToolContext,
    quantity: int,
    product_id: Optional[str] = None,
    product_name: Optional[str] = None,
) -> dict:
    """
    Purchases a specified quantity of a product.

    A user can purchase by providing a specific `product_id`.
    Alternatively, if a `product_name` is provided and there is only one
    such product available for sale in the user's location, the purchase
    will proceed automatically. If multiple are found, it will return a list
    of choices. A user cannot purchase their own product.
    """
    if not db:
        return {"error": "Database connection is not available."}
    if not isinstance(quantity, int) or quantity <= 0:
        return {"error": "Invalid input. Please provide a positive integer for quantity."}
    if not product_id and not product_name:
        return {"error": "You must provide either a product_id or a product_name to purchase."}

    session_state = tool_context.state
    user_id = session_state.get("user_id")

    if not user_id:
        return {"error": "User ID not found in session. Cannot complete purchase."}

    # --- New Logic: Find product_id if only product_name is given ---
    if not product_id and product_name:
        logging.info(f"Attempting to purchase by product_name: {product_name}")
        # Use the existing list_products function to find candidates
        available_products_response = list_products_for_sale(
            tool_context=tool_context, product_name=product_name
        )

        if "error" in available_products_response:
            return available_products_response  # Propagate error

        products = available_products_response.get("products", [])

        if not products:
            return {"error": f"Sorry, no '{product_name}' is currently available for sale in your area."}

        if len(products) > 1:
            # More than one option, so we must ask the user to clarify.
            return {
                "message": f"There are multiple sellers for '{product_name}'. Please choose one and call the purchase tool again with its product_id.",
                "choices": products,
            }

        # Exactly one product found, proceed with this one.
        product_id = products[0]['product_id']
        logging.info(f"Found unique product_id '{product_id}' for product_name '{product_name}'. Proceeding with purchase.")

    # --- Existing Logic: Purchase by product_id ---
    try:
        product_ref = db.collection(PRODUCTS_COLLECTION).document(product_id)

        # Check if the user is trying to buy their own product.
        product_doc = product_ref.get()
        if not product_doc.exists:
            return {"error": f"Product with ID {product_id} not found."}
        if product_doc.to_dict().get("seller_id") == user_id:
            return {"error": "You cannot purchase your own product."}

        transaction = db.transaction()
        # The helper now returns product data and the new quantity.
        product_data, new_quantity = _update_quantity_in_transaction(
            transaction, product_ref, quantity
        )

        # --- Pre-determine the delivery agent to avoid a second write to the order ---
        chosen_agent_id = None
        try:
            agents_ref = db.collection("delivery_agents")
            agents_stream = agents_ref.stream()
            
            available_agents = []
            for agent in agents_stream:
                agent_data = agent.to_dict()
                agent_data['id'] = agent.id
                if 'orders_assigned' in agent_data:
                    available_agents.append(agent_data)

            if not available_agents:
                logging.warning("Warning: No available delivery agents found. Skipping assignment.")
            else:
                # Simple load balancing: find the agent with the fewest orders.
                chosen_agent = min(available_agents, key=lambda x: len(x.get('orders_assigned', [])))
                chosen_agent_id = chosen_agent['agent_id']
                logging.info(f"Pre-selected agent {chosen_agent_id} for new order.")
        except Exception as agent_e:
            # Log the agent selection error but don't fail the whole purchase
            logging.warning(f"Warning: Failed to pre-select delivery agent. Error: {agent_e}")

        # Create a new document in the 'orders' collection.
        order_data = {
            "product_id": product_id,
            "buyer_id": user_id,
            "seller_id": product_data.get("seller_id"),
            "product_name": product_data.get("product_name", "Unknown Product"),
            "quantity": quantity,
            "order_time": firestore.SERVER_TIMESTAMP,
            "status": "dispatched",
            "agent_assigned": chosen_agent_id, # Set agent ID during creation
        }

        # Use a batch write to create the order and update the agent document atomically.
        batch = db.batch()

        order_ref = db.collection(ORDERS_COLLECTION).document()
        batch.set(order_ref, order_data)

        new_order_id = order_ref.id

        if chosen_agent_id:
            agent_ref = db.collection("delivery_agents").document(chosen_agent['id'])
            batch.update(agent_ref, {"orders_assigned": firestore.ArrayUnion([new_order_id])})
        
        batch.commit()
        logging.info(f"Successfully created order and assigned agent in one atomic batch write.")

        # --- Update seller's session with revenue ---
        # This is done in a separate, non-blocking step to ensure that any
        # issues with updating the seller's revenue do not prevent the buyer's
        # purchase from completing successfully.
        try:
            seller_id = product_data.get("seller_id")
            price = product_data.get("price_per_kg")
            if seller_id and price:
                revenue_from_sale = quantity * price

                # The collection name 'adk_sessions' is based on the ADK's default behavior.
                sessions_ref = db.collection("adk_sessions")
                seller_session_query = sessions_ref.where(filter=firestore.FieldFilter("user_id", "==", seller_id)).limit(1)
                seller_session_docs = list(seller_session_query.stream())

                if seller_session_docs:
                    seller_session_ref = seller_session_docs[0].reference

                    @firestore.transactional
                    def _update_seller_revenue(transaction, ref):
                        session_snapshot = ref.get(transaction=transaction)
                        if not session_snapshot.exists:
                            logging.warning(f"Seller session {ref.id} disappeared during transaction.")
                            return

                        state = session_snapshot.to_dict().get("state", {})
                        current_revenue = state.get("revenue", 0)
                        # Ensure revenue is a number before adding to it.
                        if not isinstance(current_revenue, (int, float)):
                            current_revenue = 0
                        new_revenue = current_revenue + revenue_from_sale
                        transaction.update(ref, {"state.revenue": new_revenue, "updateTime": firestore.SERVER_TIMESTAMP})
                        logging.info(f"Successfully updated revenue for seller {seller_id} by {revenue_from_sale}. New total: {new_revenue}")

                    revenue_transaction = db.transaction()
                    _update_seller_revenue(revenue_transaction, seller_session_ref)
                else:
                    logging.warning(f"Could not find an active session for seller {seller_id} to update revenue.")
        except Exception as revenue_e:
            logging.error(f"An error occurred while updating seller revenue for seller {seller_id}: {revenue_e}")

        # Add only the new order ID to the session state.
        order_ids = session_state.get("order_ids", [])
        order_ids.append(new_order_id)
        session_state["order_ids"] = order_ids


        # Prepare the state delta to update the session.
        state_delta = {"order_ids": order_ids}

        return {
            "message": f"Successfully purchased {quantity} units of {product_data.get('product_name')}. Your order has been placed. Your order ID is {new_order_id}.",
            "order_id": new_order_id,
            "product_id": product_id,
            "remaining_quantity": new_quantity,
            "state_delta": state_delta,
        }

    except Exception as e:
        logging.error(f"Error purchasing product {product_id}: {e}")
        return {"error": f"An error occurred during the purchase: {e}"}


def list_order_ids(tool_context: ToolContext) -> dict:
    """
    Retrieves the list of order IDs from the user's current session.
    """
    session_state = tool_context.state
    order_ids = session_state.get("order_ids", [])

    if not order_ids:
        return {"message": "You have not placed any orders in this session yet."}

    return {"order_ids": order_ids}


def get_order_details(order_id: str) -> dict:
    """
    Fetches the details of a specific order from the database using its ID.
    """
    if not db:
        return {"error": "Database connection is not available."}
    if not order_id:
        return {"error": "An order ID must be provided."}

    try:
        order_ref = db.collection(ORDERS_COLLECTION).document(order_id)
        order_doc = order_ref.get()

        if not order_doc.exists:
            return {"error": f"No order found with the ID: {order_id}"}

        return {"order_details": order_doc.to_dict()}
    except Exception as e:
        logging.error(f"Error fetching order details for {order_id}: {e}")
        return {"error": f"An error occurred while fetching order details: {e}"}

def sell_product(
    tool_context: ToolContext,
    product_name: str,
    quantity_available: int,
    price_per_kg: Optional[float] = None,
    product_type: Optional[str] = None,
) -> dict:
    """
    Allows a user to list a product for sale or update an existing listing.
    If the user lists a product they are already selling, it updates the quantity and optionally the price.
    Otherwise, it creates a new listing.
    """
    if not db:
        return {"error": "Database connection is not available."}

    session_state = tool_context.state
    state = session_state.get("state")
    district = session_state.get("district")
    seller_id = session_state.get("user_id")
    seller_name = session_state.get("name")

    if not all([state, district, seller_id, seller_name]):
        return {"error": "User location and identity information is missing from the session."}

    normalized_name = _singularize(product_name)

    # Robustly get the list, ensuring it's never None.
    products_listed = session_state.get("product_listed_in_market") or []
    product_to_update = None
    for product in products_listed:
        # Case-insensitive and singular/plural insensitive comparison
        if _singularize(product.get("product_name", "")).lower() == normalized_name.lower():
            product_to_update = product
            break

    # --- UPDATE EXISTING PRODUCT ---
    if product_to_update:
        try:
            product_id = product_to_update["product_id"]
            product_ref = db.collection(PRODUCTS_COLLECTION).document(product_id)

            @firestore.transactional
            def _update_in_transaction(transaction, ref):
                snapshot = ref.get(transaction=transaction)
                if not snapshot.exists:
                    raise ValueError(f"Product with ID {product_id} no longer exists in the database.")

                # Use to_dict() and dictionary's .get() for safe field retrieval.
                data = snapshot.to_dict()
                current_quantity = data.get("quantity_available", 0)
                new_quantity = current_quantity + quantity_available

                update_data = {"quantity_available": new_quantity}
                if price_per_kg is not None:
                    update_data["price_per_kg"] = price_per_kg

                transaction.update(ref, update_data)
                return new_quantity

            transaction = db.transaction()
            final_quantity = _update_in_transaction(transaction, product_ref)

            return {
                "message": f"Successfully updated your listing for '{normalized_name.title()}'. New available quantity is {final_quantity} kg.",
                "product_id": product_id,
            }
        except Exception as e:
            logging.error(f"Error updating product {product_id}: {e}")
            return {"error": f"An error occurred while updating your product: {e}"}

    # --- CREATE NEW PRODUCT ---
    else:
        # For a new product, price and type are mandatory.
        if price_per_kg is None or product_type is None:
            return {"error": f"To list a new product '{normalized_name}', you must provide a price per kg and a product type (e.g., crops, fertilizer)."}

        try:
            product_data = {
                "product_name": normalized_name.title(),
                "product_type": product_type.lower(),
                "state": state,
                "district": district,
                "seller_name": seller_name,
                "seller_id": seller_id,
                "price_per_kg": price_per_kg,
                "quantity_available": quantity_available,
                "rating": None,
                "rating_count": 0,
                "listed_at": firestore.SERVER_TIMESTAMP,
            }
            update_time, doc_ref = db.collection(PRODUCTS_COLLECTION).add(product_data)
            new_product_id = doc_ref.id

            # Add the new product to the session list
            products_listed.append(
                {"product_id": new_product_id, "product_name": normalized_name.title()}
            )
            # Prepare the state delta to update the session state.
            session_state["product_listed_in_market"] = products_listed
            state_delta = {"product_listed_in_market": products_listed}

            return {
                "message": f"Your new product '{normalized_name.title()}' has been successfully listed for sale.",
                "product_id": new_product_id,
                "state_delta": state_delta,
            }
        except Exception as e:
            logging.error(f"Error selling product: {e}")
            return {"error": f"An error occurred while listing your product: {e}"}


market_agent = Agent(
    name="market_agent",
    model="gemini-2.5-pro",
    description="Handles all market-related activities, including buying, selling, and providing real-time market price analysis for agricultural products.",
    instruction="""
    You are a specialized agricultural market analyst for India. Your purpose is to provide farmers with the latest market prices for their crops, and to help them buy and sell products on the marketplace.

    **Core Logic:**
    1.  **Understand User Intent:** Determine if the user wants to 'buy', 'sell', 'list'/'see' products, check their 'purchase history', or get the 'market price'.

    2.  **Checking Market Price:**
        - If a user asks for the market price of a crop (e.g., "what is the price of tomato?"), identify the specific commodity.
        - You MUST immediately call the `get_latest_market_price_from_session_location` tool with the identified commodity.
        - **Analyze and Summarize:** If the tool returns a dictionary with a `latest_record` key, extract the key details: `Commodity`, `Market`, `Modal_Price`, and `Arrival_Date`.
        - Present this information clearly to the user. Example Response: "Based on the latest data from [Market] on [Arrival_Date], the most common price for [Commodity] is ₹[Modal_Price] per quintal."
        - **Handle No Data:** If the tool returns a dictionary with a `message` key, it means no data was found. Relay this message politely to the user.
        - **Handle Errors:** If the tool returns a dictionary with an `error` key, analyze the error message. If the error message contains "State or District not found", you MUST ask the user for their state and district. For any other error, inform the user that you were unable to retrieve the price data at this time.

    3.  **Listing Products (User wants to see/browse):**
        - If the user asks to see available products (e.g., "what fertilizers are available?"), call the `list_products_for_sale` tool.
        - When presenting the list, format it clearly. For each product, show the `product_name`, `seller_name`, `price_per_kg`, `quantity_available`, and the `product_id`. The `product_id` is very important for the user to make a purchase.

    4.  **Buying a Product:**
        - To buy a product, the user needs to specify the `product_name` (e.g., "tomato") and the `quantity`.
        - Call the `purchase_product` tool with the `product_name` and `quantity`.
        - **Handling Tool Response:**
          - If the tool returns a `choices` list, it means there are multiple sellers. You MUST present this list to the user and ask them to choose by providing the `product_id`.
          - Relay the outcome (success or error) to the user.

    5.  **Selling or Updating a Product (Price Check Logic):**
        - If a farmer wants to sell their crops and provides a price (e.g., "I want to sell my tomatoes for 14 per kg"), you MUST first perform a market price check as described in "Checking Market Price".
        - **Step 1: Get Market Price.** Call the `get_latest_market_price_from_session_location` tool with the `commodity` name.
        - **Step 2: Compare and Advise.**
          - If the tool returns a `latest_record` with a `Modal_Price`, you MUST calculate the price per kg by dividing the `Modal_Price` by 100.
          - If the user's price is significantly lower than the calculated market price per kg, you MUST inform the user and ask for confirmation. Example: "The current market price for tomatoes is about ₹18 per kg, but you are offering ₹14. To get a better profit, you might want to increase your price. Are you sure you want to proceed with ₹14 per kg?"
          - If the user's price is higher, you should congratulate them and confirm. Example: "That's a great price! The current market average is ₹18 per kg, and you're asking for ₹20. Just to confirm, are you happy to list it at this higher price?"
        - **Step 3: Proceed on Confirmation.** Only after the user confirms their price, you should proceed to call the `sell_product` tool.
        - **Gathering Information for `sell_product`:** For a new product, you MUST have the `product_name`, `product_type`, the confirmed `price_per_kg`, and `quantity_available`. For an update, you need the `product_name` and `quantity_available`.
        - **If market data is not available:** If the tool returns a message that data is not available, inform the user and ask them to proceed with a price they think is fair, then call `sell_product`.

    6.  **Checking Purchase History:**
        - After a successful purchase, you MUST inform the user of their new `order_id`.
        - If the user asks about a specific order (e.g., "what is the status of my order?"), you MUST ask for the `order_id` if they haven't provided it.
        - Once you have the `order_id`, call the `get_order_details` tool to fetch its status and other information.
        - If the user asks for their order history or a list of their recent orders, call the `list_order_ids` tool and present the list of IDs to them.

    7.  **Handling Errors:**
        - If a tool returns an error, read the error message and inform the user clearly what went wrong. For example, if location is missing, ask them for it.
    """,
    tools=[
        list_products_for_sale,
        purchase_product,
        sell_product,
        list_order_ids,
        get_order_details,
        get_latest_market_price_from_session_location,
    ],
)