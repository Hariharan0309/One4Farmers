import requests
from datetime import datetime
import json

# --- Configuration ---
API_KEY = "579b464db66ec23bdd00000173eac38e3c8048be736d0ebd84df8bf9"
API_BASE_URL = "https://api.data.gov.in/resource/35985678-0d79-46b4-9ed6-6f13308a1d24"

# --- Test Parameters ---
TEST_STATE = "Tamil Nadu"
TEST_DISTRICT = "Villupuram"
TEST_COMMODITY = "Tomato"


def fetch_and_find_latest_price(state, district, commodity):
    """
    Fetches data from the API and finds the most recent entry using the
    correct 'Arrival_Date' key.
    """
    print(f"--- Testing API for {commodity} in {district}, {state} ---")

    params = {
        "api-key": API_KEY,
        "format": "json",
        "limit": 20,
        "filters[State]": state.title(),
        "filters[District]": district.title(),
        "filters[Commodity]": commodity.title(),
    }

    try:
        print(f"Querying API: {API_BASE_URL}")
        response = requests.get(API_BASE_URL, params=params, timeout=15)
        response.raise_for_status()
        data = response.json()

        records = data.get("records")
        if not records:
            print("\n!!! No records found for the given filters. !!!")
            return None

        print(f"\nSuccessfully fetched {len(records)} records.")

        # --- FINAL KEY NAME FIX ---
        # Check for the correct key: 'Arrival_Date' (with a capital A)
        valid_records = [record for record in records if 'Arrival_Date' in record and record['Arrival_Date']]
        
        if not valid_records:
            print("\n!!! None of the fetched records contained a valid 'Arrival_Date'. !!!")
            # Fallback to the last record if dates are truly missing
            return records[-1] if records else None

        print(f"Found {len(valid_records)} records with a valid 'Arrival_Date'.")
        
        # Sort using the correct key name
        print("Finding the most recent record by sorting...")
        latest_record = max(valid_records, key=lambda record: datetime.strptime(record['Arrival_Date'], '%d/%m/%Y'))

        return latest_record

    except requests.exceptions.RequestException as e:
        print(f"\n!!! API request failed: {e} !!!")
        return None
    except Exception as e:
        print(f"\n!!! An unexpected error occurred: {e} !!!")
        return None


# --- Main execution block ---
if __name__ == "__main__":
    latest_price_data = fetch_and_find_latest_price(
        TEST_STATE, TEST_DISTRICT, TEST_COMMODITY
    )

    if latest_price_data:
        print("\n--- Most Recent Record Found ---")
        print(json.dumps(latest_price_data, indent=4))
        print("\n--- Test Successful! ---")
    else:
        print("\n--- Test Failed ---")