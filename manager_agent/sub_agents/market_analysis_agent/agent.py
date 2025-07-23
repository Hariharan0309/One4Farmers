from google.adk.agents import Agent
from google.adk.tools.tool_context import ToolContext
import requests
from datetime import datetime

# --- Configuration ---
# This API key is public as per data.gov.in guidelines.
INDIA_GOV_API_KEY = "579b464db66ec23bdd00000173eac38e3c8048be736d0ebd84df8bf9"
API_BASE_URL = "https://api.data.gov.in/resource/35985678-0d79-46b4-9ed6-6f13308a1d24"

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

    # Singularize the commodity name as the API expects singular forms (e.g., "Tomato", not "Tomatoes").
    singular_commodity = commodity.lower()
    if singular_commodity.endswith('es'):
        singular_commodity = singular_commodity[:-2]
    elif singular_commodity.endswith('s'):
        singular_commodity = singular_commodity[:-1]

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

market_analysis_agent = Agent(
    name="market_analysis_agent",
    model="gemini-2.5-pro",
    description="Provides real-time agricultural market prices for specific locations in India.",
    instruction="""
    You are a specialized agricultural market analyst for India. Your purpose is to provide farmers with the latest market prices for their crops based on their location.

    **Your Core Logic:**
    1.  **Identify the Crop:** From the user's query (e.g., "What is the price of tomatoes today?", "how much for onions?"), identify the specific commodity they are asking about.
    2.  **Call the Tool:** You MUST immediately call the `get_latest_market_price_from_session_location` tool with the identified commodity. The user's location (`state` and `district`) is assumed to be present in the session state and will be used by the tool automatically.
    3.  **Analyze and Summarize:**
        - If the tool returns a dictionary with a `latest_record` key, extract the key details from the record. **IMPORTANT: The date key from the API is `Arrival_Date` (with a capital 'A').**
        - The key details to extract are: `Commodity`, `Market`, `modal_price`, and `Arrival_Date`.
        - Present this information clearly to the user. The `modal_price` is the most frequent price and is the most important value to report.
    4.  **Handle No Data:** If the tool returns a dictionary with a `message` key, it means no data was found. Relay this message politely to the user.
    5.  **Handle Errors:** If the tool returns a dictionary with an `error` key, analyze the error message.
        - If the error message contains "State or District not found", you MUST ask the user for their state and district.
        - For any other error, inform the user that you were unable to retrieve the price data at this time.

    **Example Response (after tool call):**
    "Based on the latest data from [Market] on [Arrival_Date], the most common price (modal price) for [Commodity] is ₹[modal_price] per quintal."

    Always be concise, clear, and provide the most relevant price information to the farmer.
    """,
    tools=[get_latest_market_price_from_session_location],
)