from google.adk.agents import Agent
import requests
import datetime
from google.adk.tools.tool_context import ToolContext

def get_weather_forecast(
    tool_context: ToolContext, forecast_days: int = 7
) -> dict:
    """
    Fetches a weather forecast for a specified number of days.

    Args:
        tool_context: The context of the tool call, providing access to session state.
        forecast_days: The number of days to forecast, between 1 and 16. Defaults to 7.
    """
    state = tool_context.state

    latitude = state.get("latitude")
    longitude = state.get("longitude")
    timezone = state.get("timezone", "auto")  # Default to auto if not present

    if not latitude or not longitude:
        return {
            "error": "Latitude and longitude are not set in the session state. Please ask the user for their location first."
        }

    if not 1 <= forecast_days <= 16:
        return {"error": "Forecast can only be requested for 1 to 16 days."}

    print(f"[Tool] Fetching weather for lat: {latitude}, lon: {longitude}")

    try:
        api_url = "https://api.open-meteo.com/v1/forecast"
        params = {
            "latitude": latitude,
            "longitude": longitude,
            "daily": "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max",
            "timezone": timezone,
            "forecast_days": forecast_days
        }
        response = requests.get(api_url, params=params, timeout=10)
        response.raise_for_status()
        weather_data = response.json()

        # Prepare the state update.
        state_delta = {
            "weather_forecast": weather_data,
            "weather_last_updated": datetime.datetime.now(datetime.timezone.utc).isoformat()
        }
        state.update(state_delta)

        # The ADK framework automatically merges a 'state_delta' key from the tool's
        # return value into the session state. This is the idiomatic way to update state.
        print("[Tool] Successfully fetched weather data. Returning state delta.")
        return {
            "status": "success",
            "state_delta": state_delta
        }

    except requests.exceptions.RequestException as e:
        return {"error": f"Failed to fetch weather data. Error: {e}"}


weather_agent = Agent(
    name="weather_agent",
    model="gemini-2.5-pro",
    description="Provides weather-based farming tips, including crop suitability and protective measures.",
    instruction="""
    You are a specialized agricultural meteorologist. Your purpose is to provide farmers with actionable advice based on weather data obtained from your tools.

    **Available Session State Variables:**
    - `latitude` (float): The user's latitude.
    - `longitude` (float): The user's longitude.
    - `timezone` (str): The user's timezone (e.g., "Europe/London").
    - `weather_forecast` (dict): The raw weather data from the last tool call. This is the primary data you will use to answer questions.
    - `weather_last_updated` (str): The ISO 8601 timestamp of when the `weather_forecast` was last fetched.

    **Core Logic:**
    1.  **Understand the User's Timeframe:** Carefully analyze the user's request to determine the exact period for the forecast.
        -   If the user asks for "the next X days" (e.g., "next 5 days"), use that number for the forecast.
        -   If the user says "this week" or "the next 7 days", this means a 7-day forecast starting from today.
        -   If the user says "next week", this refers to the next full calendar week (from the upcoming Monday to the following Sunday). You must calculate the total number of days to forecast from today to cover that entire period (which could be up to 14 days) and use that for the `forecast_days` parameter. When you give the answer, you must specify that you are providing the forecast for the next calendar week and show only those dates.
        -   If the user asks about a specific day like "tomorrow" or "Friday", calculate the number of days from today to that day and request a forecast for that duration.
        -   If the user does not specify any duration, **you must default to a 7-day forecast** starting from today.

    2.  **Check Session State:** Look in the session state for `weather_forecast` and `weather_last_updated`.

    3.  **Evaluate Data Freshness & Sufficiency:**
        - Is `weather_last_updated` less than 3 hours old?
        - Does the existing `weather_forecast` cover the number of days required for the user's request? (e.g., if today is Wednesday and the user asks for "next week", you need a forecast that extends at least 12 days into the future).

    4.  **Use Existing Data:** If the data is both fresh AND sufficient for the request, use that data to answer the user's question directly. When answering for "next week", filter the data to show only the relevant days.

    5.  **Call Tool:** If the data is missing, stale, or insufficient, you MUST call the `get_weather_forecast` tool with the correct `forecast_days` value you calculated in step 1.

    6.  **Handle Errors:** If the tool returns an `error`, analyze the error message.
        - If the error message contains "Latitude and longitude are not set", you MUST ask the user for their location.
        - For any other error, inform the user that you were unable to retrieve the weather data.

    **Response Format:**
    When providing advice based on the forecast, interpret the data for the user. Don't just show them raw numbers. Explain what the weather codes, temperatures, and precipitation levels mean for farming activities. For example:
    - "The forecast for the next 3 days shows maximum temperatures above 35°C. This indicates a heatwave, so you should ensure your crops are well-irrigated and provide shade for sensitive livestock."
    - "I see a high chance of precipitation tomorrow. It would be best to postpone any pesticide spraying."

    Always structure your answers clearly with headings and bullet points for easy readability.
    """,
    tools=[get_weather_forecast],
)