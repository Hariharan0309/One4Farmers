from google.adk.agents import Agent
from google.adk.tools import ToolContext
from vertexai.preview import rag
from google.genai import types

# --- Configuration for the "Scheme Analysis Sub-Agent" ---

# This is the unique Resource Name for the RAG corpus you created and uploaded
# your PDF documents into.
SCHEMES_LOANS_CORPUS_NAME = "projects/valued-mediator-461216-k7/locations/us-central1/ragCorpora/4611686018427387904"

# Configure retrieval parameters
rag_retrieval_config = rag.RagRetrievalConfig(
            top_k=5,
            filter=rag.Filter(vector_distance_threshold=0.7),
        )

def query_schemes_and_loans_kb(tool_context: ToolContext, query: str) -> dict:
    """
    This tool acts as a specialist for government schemes and loans.
    It searches a dedicated knowledge base (RAG corpus) using the
    rag.retrieval_query method to answer a farmer's specific question.
    It then extracts and structures the key information and source links.

    Args:
        farmer_query: The farmer's question in natural language, for example,
                      "how can I get a subsidy for drip irrigation?" or
                      "tell me about the Kisan Credit Card loan".

    Returns:
        A structured dictionary containing summaries of relevant text and a
        dedicated list of source URLs for application portals.
    """
    print(f"[Scheme Agent] Querying schemes/loans KB with: '{query}'")
    try:
        # Use the rag.retrieval_query method as requested.
        # Note that rag_corpora expects a list of strings.
        print("Performing retrieval query...")
        response = rag.retrieval_query(
            rag_resources=[
                rag.RagResource(
                    rag_corpus=SCHEMES_LOANS_CORPUS_NAME,
                )
            ],
            text=query,
            rag_retrieval_config=rag_retrieval_config,
        )
        
        # --- Process the response from retrieval_query ---
        summaries = []
        application_links = set()
        
        # The relevant information is in the 'contexts' attribute of the response
        if response.contexts and response.contexts.contexts:
            print(f"[Scheme Agent] Found {len(response.contexts.contexts)} relevant contexts. Processing...")
            for context in response.contexts.contexts:
                source_uri = context.source_uri if context.source_uri else 'Source not available'
                content_text = context.text if context.text else 'No content available.'

                # Add source URI to the links set if it's a web URL
                if source_uri and source_uri.startswith('http'):
                    application_links.add(source_uri)
                
                summaries.append({
                    "content": content_text,
                    "source": source_uri
                })

        # The final, clean output for the LLM
        final_output = {
            "summaries": summaries,
            "application_links": list(application_links)
        }
        
        if application_links:
            print(f"[Scheme Agent] Extracted links: {list(application_links)}")
            
        return final_output
        
    except Exception as e:
        error_message = f"An internal error occurred while querying the knowledge base: {e}"
        print(f"[Scheme Agent] {error_message}")
        return {"error": error_message}


scheme_analysis_agent = Agent(
    name="scheme_analysis_agent",
    model="gemini-2.5-pro",
    description="Provides detailed information on government schemes, subsidies, and loans for farmers by searching a dedicated knowledge base.",
    instruction="""
    You are a specialist in Indian government agricultural schemes and loans. Your primary function is to help farmers understand and apply for financial support.

    **Your Core Logic:**
    1.  **Receive the Query:** You will be given a farmer's question about a specific need (e.g., "subsidy for drip irrigation", "Kisan Credit Card details" , "list the schemes available").
    2.  **Call the Tool:** You MUST immediately call the `query_schemes_and_loans_kb` tool with the user's full query to search the knowledge base.
    3.  **Analyze the Results:**
        - If the tool returns a `context` with text chunks, synthesize the information into a simple, easy-to-understand explanation.
        - Clearly list any eligibility requirements you find in the text.
        - If the tool returns a list of `application_links`, you MUST present these links to the user under a clear heading like "Application Links:".
    4.  **Handle No Data:** If the tool returns no relevant context, inform the user that you could not find specific information on that topic.
    5.  **Handle Errors:** If the tool returns an `error`, apologize to the user and let them know you were unable to retrieve the information.

    **Response Format:**
    Structure your answer clearly. Use headings (like "Scheme Details", "Eligibility", "Application Links") and bullet points to make the information easy to digest.
    """,
    tools=[query_schemes_and_loans_kb],
)