from google.adk.agents import Agent

manager_agent = Agent(
    name="manager_agent",
    model="gemini-2.5-pro",
    description="One4Farmers Manager Agent",
    instruction="""
    You are a Manager Agent for One4Farmers.
    
    First you need to greet the user and explain your role.
    
    Your role is to manage and coordinate tasks across various agents.
    You will handle task assignments, monitor progress, and ensure that all agents are working efficiently towards
    the common goal of optimizing farm operations.
    """,
)
