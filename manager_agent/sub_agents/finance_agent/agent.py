from google.adk.agents import Agent
from google.adk.tools.tool_context import ToolContext
import os
import requests
from datetime import datetime
import logging

# --- Configuration ---
INDIA_GOV_API_KEY = os.environ.get("INDIA_GOV_API_KEY", "579b464db66ec23bdd00000173eac38e3c8048be736d0ebd84df8bf9")
API_BASE_URL = "https://api.data.gov.in/resource/35985678-0d79-46b4-9ed6-6f13308a1d24"

# A predefined list of common, high-value crops to check for profitability.
COMMON_CROPS_TO_EVALUATE = ["Tomato", "Onion", "Potato", "Paddy", "Wheat", "Cotton", "Sugarcane", "Maize"]

def _singularize(name: str) -> str:
    """A very basic singularizer to handle common plurals."""
    lowered = name.lower()
    if lowered.endswith('oes') and len(lowered) > 3:
        return name[:-2]
    if lowered.endswith('s') and not lowered.endswith('ss') and len(lowered) > 2:
        return name[:-1]
    return name

def get_crop_profitability_plan(tool_context: ToolContext) -> dict:
    """
    Analyzes market data for common crops to create a profitability plan
    for a farm of a given size (in acres). Uses the user's location from the session.
    It also includes the user's total revenue earned so far from the app.
    """
    session_state = tool_context.state
    acres = session_state.get("acres")
    state = session_state.get("state")
    district = session_state.get("district")
    revenue = session_state.get("revenue", 0)

    if not all([acres, state, district]):
        return {"error": "Farm size (acres), state, and district must be set in the session to generate a financial plan."}

    profitable_crops = []
    print(f"[Finance Tool] Analyzing profitability for a {acres}-acre farm in {district}, {state}.")

    for crop in COMMON_CROPS_TO_EVALUATE:
        params = {
            "api-key": INDIA_GOV_API_KEY,
            "format": "json",
            "limit": 10,
            "filters[State]": state.title(),
            "filters[District]": district.title(),
            "filters[Commodity]": _singularize(crop).title(),
        }
        try:
            response = requests.get(API_BASE_URL, params=params, timeout=10)
            response.raise_for_status()
            data = response.json()
            records = data.get("records")
            if records:
                # Find the record with the highest modal price from a recent date
                valid_records = [r for r in records if r.get('Modal_Price') and r.get('Arrival_Date')]
                if valid_records:
                    latest_record = max(valid_records, key=lambda r: datetime.strptime(r['Arrival_Date'], '%d/%m/%Y'))
                    modal_price = float(latest_record.get("Modal_Price", 0))
                    # Price is per quintal (100 kg)
                    price_per_kg = modal_price / 100
                    profitable_crops.append({"crop": crop, "price_per_kg": price_per_kg})
                    print(f"[Finance Tool] Found price for {crop}: ₹{price_per_kg:.2f}/kg")

        except requests.exceptions.RequestException as e:
            logging.warning(f"[Finance Tool] Could not fetch price for {crop}. Error: {e}")
            continue # Move to the next crop

    if not profitable_crops:
        return {"error": "Could not retrieve enough market data for your location to create a reliable plan."}

    # Sort by price to find the most profitable crops
    profitable_crops.sort(key=lambda x: x["price_per_kg"], reverse=True)

    # Simple plan: suggest the top 3 most profitable crops.
    plan = {
        "farm_size_acres": acres,
        "location": f"{district}, {state}",
        "total_revenue_so_far": revenue,
        "recommendations": [f"Based on current market prices in your area, focusing on **{profitable_crops[i]['crop']}** (market price approx. ₹{profitable_crops[i]['price_per_kg']:.2f}/kg) could be highly profitable." for i in range(min(3, len(profitable_crops)))],
        "disclaimer": "This advice is based on recent market prices and does not account for cultivation costs, soil type, or water availability. Please consider these factors."
    }

    return {"profitability_plan": plan}


def get_session_revenue(tool_context: ToolContext) -> dict:
    """
    Retrieves the total revenue earned by the user from sales within the app.
    The revenue is fetched from the user's current session state.
    """
    session_state = tool_context.state
    revenue = session_state.get("revenue", 0)

    # Ensure revenue is a number
    if not isinstance(revenue, (int, float)):
        revenue = 0

    return {"total_revenue": revenue}


finance_agent = Agent(
    name="finance_agent",
    model="gemini-2.5-pro",
    description="Provides financial planning and crop cultivation advice to maximize farm yield and profitability based on farm size and location.",
    instruction="""
    You are an expert agricultural financial advisor. Your goal is to help farmers make profitable decisions about which crops to grow and to track their earnings.

    **Core Logic:**
    1.  **Receive the Query:** The user will ask for advice on what to grow, how to make more money, for a financial plan for their farm, or about their revenue.
    2.  **IF the user asks about their revenue, earnings, or income:**
        - You MUST call the `get_session_revenue` tool.
        - Present the `total_revenue` to the user clearly. Example: "Your total revenue from sales on the app so far is ₹[total_revenue]."
    3.  **IF the user asks for a financial or profitability plan:**
        - You MUST immediately call the `get_crop_profitability_plan` tool. This tool will analyze market data and return a plan.
        - **Present the Plan:** If the tool returns a `profitability_plan`, present the recommendations clearly to the user. You MUST also mention their `total_revenue_so_far` as part of the financial overview.
        - You MUST include the `disclaimer` from the tool's response. Frame the response in a helpful, advisory tone.
    4.  **Handle Errors:** If a tool returns an error stating that session information is missing, you MUST ask the user for the missing details (e.g., "To create a plan, I need to know the size of your farm in acres. Could you please tell me?"). For any other error, inform the user that you were unable to generate a plan at this time.
    """,
    tools=[get_crop_profitability_plan, get_session_revenue],
)