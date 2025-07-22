import requests
import vertexai
from vertexai.preview import generative_models as genai
from google.adk.tools.tool_context import ToolContext


def analyze_plant_image_from_url(tool_context: ToolContext) -> str:
    """
    Downloads an image from a public URL, analyzes it for plant diseases
    using a multimodal model, and returns a detailed report.

    Args:
        url: The public URL of the image to analyze.

    Returns:
        A string containing the analysis report.
    """
    url = tool_context.state.get("image_url", [])
    #url = "https://firebasestorage.googleapis.com/v0/b/valued-mediator-461216-k7.firebasestorage.app/o/plant_diseases%2F1752943819354.jpg?alt=media&token=1e1963bb-0eb2-4161-bb2f-2f379e7184a2"
    print(f"[Tool] Analyzing image from URL: {url}")
    try:
        # 1. Download the image data
        response = requests.get(url, timeout=15)
        response.raise_for_status()  # Raise an exception for bad status codes
        image_data = response.content

        # Determine MIME type from URL or headers if possible
        mime_type = response.headers.get('content-type', 'image/jpeg')

        # 2. Prepare the prompt and image for the multimodal model
        image_part = genai.Part.from_data(data=image_data, mime_type=mime_type)
        prompt = """
        You are an expert plant pathologist. Analyze the provided image of a plant and provide a detailed report.
        Your report should include:
        1.  A description of the symptoms you observe.
        2.  The likely disease, pest, or deficiency causing these symptoms.
        3.  Recommendations for both organic and chemical treatments.
        4.  Preventative measures to avoid this issue in the future.
        """

        # 3. Call the multimodal model for analysis
        model = genai.GenerativeModel("gemini-2.5-pro")
        analysis_response = model.generate_content([prompt, image_part])

        print("[Tool] Analysis complete.")
        return analysis_response.text

    except requests.exceptions.RequestException as e:
        error_message = f"Failed to download image from URL: {url}. Error: {e}"
        print(f"[Tool] {error_message}")
        return error_message
    except Exception as e:
        error_message = f"An unexpected error occurred during image analysis: {e}"
        print(f"[Tool] {error_message}")
        return error_message
