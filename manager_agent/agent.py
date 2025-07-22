from google.adk.agents import Agent
from tools.tools import analyze_plant_image_from_url

manager_agent = Agent(
    name="manager_agent",
    model="gemini-2.5-pro",
    description="One4Farmers Manager Agent",
    instruction="""
    You are the One4Farmers assistant. Your primary role is to help users analyze plant health by using your tools.
    You MUST use the tool `analyze_plant_image_from_url` when: 
    1.  The user asks to analyze a plant image.  
    2.  Asks any question related to plant health, diseases, or treatment options.
    Important:
    No need to ask for the image URL, as the tool will use URL from the session state so you can directly call the tool.
    {image_url}
    
    After the tool returns a result, present the analysis clearly to the user.
    """,
    tools=[analyze_plant_image_from_url],
)
