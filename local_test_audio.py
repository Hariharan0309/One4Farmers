from google import genai
from google.genai import types
import wave
import os
import base64
import struct

GOOGLE_API_KEY="AIzaSyByjL39m5mXlJxNZOwRfpsN2ewVHZfjuMc"


# Set up the wave file to save the output:
def wave_file(filename, pcm, channels=1, rate=24000, sample_width=2):
   print(f"\nWriting audio file with parameters:")
   print(f"Channels: {channels}")
   print(f"Sample rate: {rate}")
   print(f"Sample width: {sample_width}")
   print(f"Data length: {len(pcm)} bytes")

   with wave.open(filename, "wb") as wf:
      wf.setnchannels(channels)
      wf.setsampwidth(sample_width)
      wf.setframerate(rate)
      wf.writeframes(pcm)

PROMPT = "Say excitedly: Thats right Gemini now has Text to speech!"

VOICE = 'Kore'

client = genai.Client(api_key=GOOGLE_API_KEY)

response = client.models.generate_content(
   model="gemini-2.5-pro-preview-tts",
   contents=PROMPT,
   config=types.GenerateContentConfig(
      response_modalities=["audio"],
      speech_config=types.SpeechConfig(
         voice_config=types.VoiceConfig(
            prebuilt_voice_config=types.PrebuiltVoiceConfig(
               voice_name=VOICE,
            )
         )
      ),
   )
)

# Debug the response structure
print("\nResponse structure:")
print(f"Number of candidates: {len(response.candidates)}")
print(f"Content parts: {len(response.candidates[0].content.parts)}")
print(f"Part type: {type(response.candidates[0].content.parts[0])}")

data = response.candidates[0].content.parts[0].inline_data.data

rate = 24000
file_name = f'single_voice_out.wav'

print(f"\nSaving sample rate: {rate}")
wave_file(file_name, data, rate=rate)