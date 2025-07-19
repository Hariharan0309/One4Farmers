from __future__ import annotations

import os
import shutil
import subprocess
from typing import Optional

import click

import google.auth
from google.auth import impersonated_credentials
from google.oauth2 import service_account

temp_folder = 'tmp'
agent_folder = 'manager_agent'
adk_app = 'adk_app'
requirements_file = 'requirements.txt'
env_file = '.env'
project = 'valued-mediator-461216-k7'
region = 'us-central1'
staging_bucket = 'gs://one4farmers'
display_name = 'One4Farmers'
description = 'A farm expert agent for farmers to manage their farm operations efficiently.'
trace_to_cloud = False  # Set to False if you don't want to trace to cloud
CUSTOM_SA_EMAIL = "reasoning-engine-runner@valued-mediator-461216-k7.iam.gserviceaccount.com"

_AGENT_ENGINE_APP_TEMPLATE = """
from agent import root_agent
from vertexai.preview.reasoning_engines import AdkApp

adk_app = AdkApp(
  agent=root_agent,
  enable_tracing={trace_to_cloud_option},
)
"""

source_credentials, _ = google.auth.default()
target_credentials = impersonated_credentials.Credentials(
    source_credentials=source_credentials,
    target_principal=CUSTOM_SA_EMAIL,
    target_scopes=['https://www.googleapis.com/auth/cloud-platform']
)



def _resolve_project(project_in_option: Optional[str]) -> str:
  if project_in_option:
    return project_in_option

  result = subprocess.run(
      ['gcloud', 'config', 'get-value', 'project'],
      check=True,
      capture_output=True,
      text=True,
  )
  project = result.stdout.strip()
  click.echo(f'Use default project: {project}')
  return project



if os.path.exists(temp_folder):
    click.echo('Removing existing files')
    shutil.rmtree(temp_folder)

try:
    click.echo('Copying agent source code...')
    shutil.copytree(agent_folder, temp_folder)
    click.echo('Copying agent source code complete.')

    click.echo('Initializing Vertex AI...')
    import sys

    import vertexai
    from vertexai import agent_engines

    sys.path.append(temp_folder)
    project = _resolve_project(project)

    click.echo('Resolving files and dependencies...')
    if not requirements_file:
        # Attempt to read requirements from requirements.txt in the dir (if any).
        requirements_txt_path = os.path.join(temp_folder, 'requirements.txt')
        if not os.path.exists(requirements_txt_path):
            click.echo(f'Creating {requirements_txt_path}...')
        with open(requirements_txt_path, 'w', encoding='utf-8') as f:
            f.write('google-cloud-aiplatform[adk,agent_engines]')
        click.echo(f'Created {requirements_txt_path}')
        requirements_file = requirements_txt_path
    env_vars = None
    if not env_file:
        # Attempt to read the env variables from .env in the dir (if any).
        env_file = os.path.join(temp_folder, '.env')
    if os.path.exists(env_file):
        from dotenv import dotenv_values

        click.echo(f'Reading environment variables from {env_file}')
        env_vars = dotenv_values(env_file)
        if 'GOOGLE_CLOUD_PROJECT' in env_vars:
            env_project = env_vars.pop('GOOGLE_CLOUD_PROJECT')
        if env_project:
            if project:
                click.secho(
                    'Ignoring GOOGLE_CLOUD_PROJECT in .env as `--project` was'
                    ' explicitly passed and takes precedence',
                    fg='yellow',
                )
            else:
                project = env_project
                click.echo(f'{project=} set by GOOGLE_CLOUD_PROJECT in {env_file}')
        if 'GOOGLE_CLOUD_LOCATION' in env_vars:
            env_region = env_vars.pop('GOOGLE_CLOUD_LOCATION')
        if env_region:
            if region:
                click.secho(
                    'Ignoring GOOGLE_CLOUD_LOCATION in .env as `--region` was'
                    ' explicitly passed and takes precedence',
                    fg='yellow',
                )
            else:
                region = env_region
                click.echo(f'{region=} set by GOOGLE_CLOUD_LOCATION in {env_file}')

    vertexai.init(
        project=project,
        location=region,
        staging_bucket=staging_bucket,
        credentials=target_credentials 
    )
    click.echo('Vertex AI initialized.')

    adk_app_file = f'{adk_app}.py'
    adk_app_path_in_temp = os.path.join(temp_folder, adk_app_file)

    # Check if the user has provided their own adk_app.py
    if not os.path.exists(adk_app_path_in_temp):
        click.echo(f"'{adk_app_file}' not found in agent folder. Creating a default one.")
        with open(adk_app_path_in_temp, 'w', encoding='utf-8') as f:
            f.write(
                _AGENT_ENGINE_APP_TEMPLATE.format(
                    trace_to_cloud_option=trace_to_cloud
                )
            )
        click.echo(f'Created {adk_app_path_in_temp}')
    else:
        click.echo(f"Using existing '{adk_app_file}' from agent folder.")

    click.echo('Files and dependencies resolved')

    click.echo('Deploying to agent engine...')
    agent_engine = agent_engines.ModuleAgent(
        module_name=adk_app,
        agent_name='adk_app',
        register_operations={
            '': [
                'get_session',
                'list_sessions',
                'create_session',
                'delete_session',
            ],
            'async': [
                'async_get_session',
                'async_list_sessions',
                'async_create_session',
                'async_delete_session',
            ],
            'async_stream': ['async_stream_query'],
            'stream': ['stream_query', 'streaming_agent_run_with_events'],
        },
       sys_paths=[temp_folder],
    )

    agent_engines.create(
        agent_engine=agent_engine,
        requirements=requirements_file,
        display_name=display_name,
        description=description,
        env_vars=env_vars,
        extra_packages=[temp_folder]
    )
finally:
    click.echo(f'Cleaning up the temp folder: {temp_folder}')
    shutil.rmtree(temp_folder)